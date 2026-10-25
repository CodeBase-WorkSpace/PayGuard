package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.LedgerEntryType;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.LedgerEntryId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.TransactionId;
import domain.DomainValidation;

import java.time.Instant;

/**
 * An immutable accounting entry, with entity equality by entry identifier.
 *
 * <p>The amount is strictly positive; direction is expressed by {@code entryType}.
 * Sequence numbers are non-negative and ordered within an account. Allocation,
 * uniqueness, immutability in storage, and tenant/account ownership belong to persistence.
 * A single entry cannot establish double-entry balance; {@link Transaction} validates a batch.
 *
 * @param id             identity of this entry
 * @param transactionId  transaction owning this entry
 * @param accountId      account affected by this entry
 * @param amount         exact, strictly positive posting magnitude
 * @param entryType      debit or credit direction
 * @param sequenceNumber non-negative account posting sequence
 * @param timestamp      posting timestamp
 */
public record LedgerEntry(
        LedgerEntryId id,
        TransactionId transactionId,
        AccountId accountId,
        Money amount,
        LedgerEntryType entryType,
        long sequenceNumber,
        Instant timestamp) {

    /**
     * Validates all entry-local invariants without changing the monetary amount.
     */
    public LedgerEntry {
        DomainValidation.requireNonNull(id, "Ledger entry id");
        DomainValidation.requireNonNull(transactionId, "Transaction id");
        DomainValidation.requireNonNull(accountId, "Account id");
        DomainValidation.requireNonNull(amount, "Amount");
        DomainValidation.requireNonNull(entryType, "Entry type");
        DomainValidation.requireNonNull(timestamp, "Timestamp");
        amount.assertNonNegative();
        if (amount.isZero()) {
            throw new InvalidWalletStateException("Ledger entry amount must be positive");
        }
        if (sequenceNumber < 0) {
            throw new InvalidWalletStateException("Sequence number cannot be negative");
        }
    }

    /**
     * Compares entry identity, not posting content; never use this to validate retry payloads.
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof LedgerEntry entry && id.equals(entry.id);
    }

    /**
     * Returns a stable hash derived only from the immutable identity.
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
