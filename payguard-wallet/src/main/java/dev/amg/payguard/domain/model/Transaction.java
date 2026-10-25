package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.LedgerEntryType;
import dev.amg.payguard.domain.enums.TransactionStatus;
import dev.amg.payguard.domain.enums.TransactionType;
import dev.amg.payguard.domain.exception.CurrencyMismatchException;
import dev.amg.payguard.domain.exception.DoubleEntryImbalanceException;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.exception.TransactionAlreadySettledException;
import dev.amg.payguard.domain.value_object.*;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A single-currency transfer with an optional additional fee paid by the source.
 *
 * <p>Explicit policy: {@code totalAmount} is the principal delivered to the destination;
 * the source is debited {@code totalAmount + feeAmount}. Separate fee accounts receive
 * fee credits. Fees may be split across accounts. FX, netted fees and partial reversals
 * are not supported by this model.
 *
 * <p>Mutable and command-confined. Settlement validates postings but does not write them.
 * The application must atomically persist transaction state, all entries, affected wallet
 * balances, idempotency result and outbox in the same local database transaction.
 */
public final class Transaction extends AggregateRoot<TransactionId, DomainEvent> {
    private final IdempotencyKey idempotencyKey;
    private final TransactionType type;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money totalAmount;
    private final Money feeAmount;
    private final Instant createdAt;
    private TransactionStatus status;
    private String failureReason;
    private Instant settledAt;
    private TransactionId reversalTransactionId;
    private Instant reversedAt;

    private Transaction(TransactionId id, IdempotencyKey idempotencyKey, TransactionType type,
                        AccountId sourceAccountId, AccountId destinationAccountId, Money totalAmount, Money feeAmount,
                        TransactionStatus status, String failureReason, Instant createdAt, Instant settledAt,
                        TransactionId reversalTransactionId, Instant reversedAt) {
        super(id);
        this.idempotencyKey = DomainValidation.requireNonNull(idempotencyKey, "Idempotency key");
        this.type = DomainValidation.requireNonNull(type, "Transaction type");
        this.sourceAccountId = DomainValidation.requireNonNull(sourceAccountId, "Source account id");
        this.destinationAccountId =
                DomainValidation.requireNonNull(destinationAccountId, "Destination account id");
        this.totalAmount = DomainValidation.requireNonNull(totalAmount, "Total amount");
        this.feeAmount = DomainValidation.requireNonNull(feeAmount, "Fee amount");
        this.status = DomainValidation.requireNonNull(status, "Transaction status");
        this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
        totalAmount.assertNonNegative();
        feeAmount.assertNonNegative();
        if (totalAmount.isZero()) {
            throw new InvalidWalletStateException("Transaction principal must be positive");
        }
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new InvalidWalletStateException("Source and destination accounts must differ");
        }
        if (!totalAmount.currency().equals(feeAmount.currency())) {
            throw new CurrencyMismatchException("Principal and fee currencies must match");
        }
        validateSnapshot(status, failureReason, settledAt, reversalTransactionId, reversedAt);
        this.failureReason = failureReason;
        this.settledAt = settledAt;
        this.reversalTransactionId = reversalTransactionId;
        this.reversedAt = reversedAt;
    }

    /**
     * Creates a new, unsettled transaction without performing IO or posting funds.
     */
    public static Transaction initiate(TransactionId id, IdempotencyKey idempotencyKey,
                                       TransactionType type, AccountId sourceAccountId, AccountId destinationAccountId,
                                       Money totalAmount, Money feeAmount, Instant now) {
        return new Transaction(id, idempotencyKey, type, sourceAccountId, destinationAccountId,
                totalAmount, feeAmount, TransactionStatus.INITIATED, null, now, null, null, null);
    }

    /**
     * Restores a consistent persisted snapshot without replaying any state transitions.
     */
    public static Transaction restore(TransactionId id, IdempotencyKey idempotencyKey,
                                      TransactionType type, AccountId sourceAccountId, AccountId destinationAccountId,
                                      Money totalAmount, Money feeAmount, TransactionStatus status, String failureReason,
                                      Instant createdAt, Instant settledAt, TransactionId reversalTransactionId, Instant reversedAt) {
        return new Transaction(id, idempotencyKey, type, sourceAccountId, destinationAccountId,
                totalAmount, feeAmount, status, failureReason, createdAt, settledAt,
                reversalTransactionId, reversedAt);
    }

    /**
     * Moves an initiated transaction into pending settlement exactly once.
     */
    public void markPendingSettlement() {
        if (status != TransactionStatus.INITIATED) {
            throw new InvalidWalletStateException("Only an initiated transaction can become pending");
        }
        status = TransactionStatus.PENDING_SETTLEMENT;
    }

    /**
     * Validates the complete posting batch and marks an open transaction successful.
     * All validation happens before mutation. This is not a database commit.
     */
    public void markSettled(List<LedgerEntry> entries, Instant now) {
        assertOpen();
        validateTime(now);
        validatePosting(entries, now);
        status = TransactionStatus.SUCCESSFUL;
        settledAt = now;
    }

    /**
     * Checks entry identity, account sequences, currency, time, principal/fee allocation
     * and equal debit/credit totals without changing transaction state.
     */
    public void validatePosting(List<LedgerEntry> entries, Instant now) {
        DomainValidation.requireNonNull(entries, "Ledger entries");
        validateTime(now);
        for (LedgerEntry entry : entries) {
            DomainValidation.requireNonNull(entry, "Ledger entry");
        }
        List<LedgerEntry> posting = List.copyOf(entries);
        if (posting.size() < 2) {
            throw new DoubleEntryImbalanceException("A posting requires at least two entries");
        }
        Set<LedgerEntryId> entryIds = new HashSet<>();
        Set<AccountSequence> accountSequences = new HashSet<>();
        Money debits = Money.zero(totalAmount.currency());
        Money credits = Money.zero(totalAmount.currency());
        Money sourceDebits = Money.zero(totalAmount.currency());
        Money destinationCredits = Money.zero(totalAmount.currency());
        Money feeCredits = Money.zero(totalAmount.currency());

        for (LedgerEntry entry : posting) {
            if (!getId().equals(entry.transactionId())) {
                throw new InvalidWalletStateException("Entry belongs to another transaction");
            }
            if (!entryIds.add(entry.id())) {
                throw new InvalidWalletStateException("Duplicate ledger entry identity in batch");
            }
            if (!accountSequences.add(new AccountSequence(entry.accountId(), entry.sequenceNumber()))) {
                throw new InvalidWalletStateException("Duplicate account sequence in batch");
            }
            if (!totalAmount.currency().equals(entry.amount().currency())) {
                throw new CurrencyMismatchException("Posting currency must match transaction currency");
            }
            if (entry.timestamp().isBefore(createdAt) || entry.timestamp().isAfter(now)) {
                throw new InvalidWalletStateException("Posting timestamp is outside transaction interval");
            }
            if (entry.entryType() == LedgerEntryType.DEBIT) {
                if (!entry.accountId().equals(sourceAccountId)) {
                    throw new InvalidWalletStateException("Only the source account may be debited");
                }
                debits = debits.add(entry.amount());
                sourceDebits = sourceDebits.add(entry.amount());
            } else {
                if (entry.accountId().equals(sourceAccountId)) {
                    throw new InvalidWalletStateException("Source account cannot receive credit in this posting");
                }
                credits = credits.add(entry.amount());
                if (entry.accountId().equals(destinationAccountId)) {
                    destinationCredits = destinationCredits.add(entry.amount());
                } else {
                    feeCredits = feeCredits.add(entry.amount());
                }
            }
        }
        if (!debits.equals(credits)) {
            throw new DoubleEntryImbalanceException("Debit and credit totals differ");
        }
        if (!sourceDebits.equals(totalAmount.add(feeAmount))
                || !destinationCredits.equals(totalAmount)
                || !feeCredits.equals(feeAmount)) {
            throw new InvalidWalletStateException("Posting does not match principal and fee allocation");
        }
    }

    /**
     * Fails only an open transaction; invalid reasons do not alter state.
     */
    public void markFailed(String reason) {
        assertOpen();
        String validatedReason = DomainValidation.requireText(reason, "Failure reason");
        status = TransactionStatus.FAILED;
        failureReason = validatedReason;
    }

    /**
     * Links a successful, full, zero-fee compensating REFUND transaction.
     *
     * <p>This deliberately refuses fee-bearing reversals until a fee-refund posting policy
     * is defined. The original entries are never changed. Persist the reversal and this
     * link atomically, with a unique original/reversal relationship and tenant checks.
     */
    public void markReversed(Transaction reversal) {
        DomainValidation.requireNonNull(reversal, "Reversal transaction");
        if (status != TransactionStatus.SUCCESSFUL) {
            throw new InvalidWalletStateException("Only a successful transaction can be reversed");
        }
        if (reversal == this || getId().equals(reversal.getId())) {
            throw new InvalidWalletStateException("A transaction cannot reverse itself");
        }
        if (reversal.status != TransactionStatus.SUCCESSFUL || reversal.type != TransactionType.REFUND) {
            throw new InvalidWalletStateException("Reversal must be a successful refund transaction");
        }
        if (!feeAmount.isZero() || !reversal.feeAmount.isZero()) {
            throw new InvalidWalletStateException("Fee-bearing reversals require an explicit refund policy");
        }
        if (!sourceAccountId.equals(reversal.destinationAccountId)
                || !destinationAccountId.equals(reversal.sourceAccountId)
                || !totalAmount.equals(reversal.totalAmount)) {
            throw new InvalidWalletStateException("Reversal must return the full original principal");
        }
        if (reversal.createdAt.isBefore(settledAt) || reversal.settledAt.isBefore(settledAt)) {
            throw new InvalidWalletStateException("Reversal cannot precede original settlement");
        }
        reversalTransactionId = reversal.getId();
        reversedAt = reversal.settledAt;
        status = TransactionStatus.REVERSED;
    }

    private void validateSnapshot(TransactionStatus status, String reason, Instant settlement,
                                  TransactionId reversalId, Instant reversalTime) {
        if (settlement != null) {
            validateTime(settlement);
        }
        switch (status) {
            case INITIATED, PENDING_SETTLEMENT -> {
                if (reason != null || settlement != null || reversalId != null || reversalTime != null) {
                    throw new InvalidWalletStateException("Open transaction contains terminal metadata");
                }
            }
            case FAILED -> {
                DomainValidation.requireText(reason, "Failure reason");
                if (settlement != null || reversalId != null || reversalTime != null) {
                    throw new InvalidWalletStateException("Failed transaction contains settlement metadata");
                }
            }
            case SUCCESSFUL -> {
                if (reason != null || settlement == null || reversalId != null || reversalTime != null) {
                    throw new InvalidWalletStateException("Successful transaction metadata is inconsistent");
                }
            }
            case REVERSED -> {
                if (reason != null || settlement == null || reversalId == null || reversalTime == null) {
                    throw new InvalidWalletStateException("Reversed transaction metadata is incomplete");
                }
                if (getId().equals(reversalId) || reversalTime.isBefore(settlement) || !feeAmount.isZero()) {
                    throw new InvalidWalletStateException("Invalid reversal reference, time or fee policy");
                }
            }
        }
    }

    private void assertOpen() {
        if (status == TransactionStatus.SUCCESSFUL) {
            throw new TransactionAlreadySettledException("Transaction is already settled");
        }
        if (status != TransactionStatus.INITIATED && status != TransactionStatus.PENDING_SETTLEMENT) {
            throw new InvalidWalletStateException("Transaction is terminal: " + status);
        }
    }

    private void validateTime(Instant now) {
        DomainValidation.requireNonNull(now, "Operation time");
        if (now.isBefore(createdAt)) {
            throw new InvalidWalletStateException("Operation time precedes transaction creation");
        }
    }

    /**
     * A local collision key, deliberately not a new public domain abstraction.
     */
    private record AccountSequence(AccountId accountId, long sequenceNumber) {
    }

    /**
     * Returns the exact client-supplied retry key; uniqueness is scoped in persistence.
     */
    public IdempotencyKey getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Returns the business transaction classification.
     */
    public TransactionType getType() {
        return type;
    }

    /**
     * Returns the account paying principal plus fee.
     */
    public AccountId getSourceAccountId() {
        return sourceAccountId;
    }

    /**
     * Returns the account receiving the principal.
     */
    public AccountId getDestinationAccountId() {
        return destinationAccountId;
    }

    /**
     * Returns the destination principal, excluding the additional source-paid fee.
     */
    public Money getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the additional fee, possibly zero.
     */
    public Money getFeeAmount() {
        return feeAmount;
    }

    /**
     * Returns the current lifecycle state.
     */
    public TransactionStatus getStatus() {
        return status;
    }

    /**
     * Returns a reason only when the transaction failed.
     */
    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    /**
     * Returns the immutable initiation timestamp.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the original settlement time, retained after reversal.
     */
    public Optional<Instant> getSettledAt() {
        return Optional.ofNullable(settledAt);
    }

    /**
     * Returns the successful compensating transaction identity when reversed.
     */
    public Optional<TransactionId> getReversalTransactionId() {
        return Optional.ofNullable(reversalTransactionId);
    }

    /**
     * Returns the compensating settlement time when reversed.
     */
    public Optional<Instant> getReversedAt() {
        return Optional.ofNullable(reversedAt);
    }
}
