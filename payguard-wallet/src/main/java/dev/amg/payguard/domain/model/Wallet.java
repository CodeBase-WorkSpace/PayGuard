package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.LedgerEntryType;
import dev.amg.payguard.domain.enums.WalletStatus;
import dev.amg.payguard.domain.event.WalletClosedEvent;
import dev.amg.payguard.domain.event.WalletCreatedEvent;
import dev.amg.payguard.domain.event.WalletStatusChangedEvent;
import dev.amg.payguard.domain.exception.CurrencyMismatchException;
import dev.amg.payguard.domain.exception.InsufficientFundException;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.exception.WalletFrozenException;
import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.UserId;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;
import java.time.Instant;
import java.util.Currency;

/**
 * A single-currency, non-overdraft customer wallet backed by an explicit ledger account.
 *
 * <p>Always maintains {@code 0 <= reservedBalance <= bookBalance}. Credits increase
 * this customer-liability balance; debits decrease it. This is not a generic GL account.
 *
 * <p>Instances are mutable and MUST be confined to one command/unit of work, never
 * shared across threads or cached as live aggregates. Repository locking or versioned
 * conditional updates are required for concurrent commands. The loaded version is an
 * expected persistence version, not an in-memory mutation counter.
 */
public final class Wallet extends AggregateRoot<WalletId, DomainEvent> {
  private final AccountId accountId;
  private final UserId userId;
  private final Currency currency;
  private final Instant createdAt;
  private final long version;
  private WalletStatus status;
  private Money bookBalance;
  private Money reservedBalance;
  private Instant updatedAt;
  private long lastAppliedSequenceNumber;

  private Wallet(WalletId id, AccountId accountId, UserId userId, Currency currency,
      WalletStatus status, Money bookBalance, Money reservedBalance, long version,
      long lastAppliedSequenceNumber, Instant createdAt, Instant updatedAt) {
    super(id);
    this.accountId = DomainValidation.requireNonNull(accountId, "Ledger account id");
    this.userId = DomainValidation.requireNonNull(userId, "User id");
    this.currency = DomainValidation.requireNonNull(currency, "Currency");
    this.status = DomainValidation.requireNonNull(status, "Wallet status");
    this.bookBalance = DomainValidation.requireNonNull(bookBalance, "Book balance");
    this.reservedBalance = DomainValidation.requireNonNull(reservedBalance, "Reserved balance");
    this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
    this.updatedAt = DomainValidation.requireNonNull(updatedAt, "Update time");
    requireCurrency(bookBalance);
    requireCurrency(reservedBalance);
    bookBalance.assertNonNegative();
    reservedBalance.assertNonNegative();
    if (reservedBalance.isGreaterThan(bookBalance)) {
      throw new InvalidWalletStateException("Reserved balance cannot exceed book balance");
    }
    if (version < 0 || lastAppliedSequenceNumber < -1) {
      throw new InvalidWalletStateException("Invalid persisted version or posting sequence");
    }
    if (updatedAt.isBefore(createdAt)) {
      throw new InvalidWalletStateException("Update time precedes creation time");
    }
    if (status == WalletStatus.CLOSED && (!bookBalance.isZero() || !reservedBalance.isZero())) {
      throw new InvalidWalletStateException("A closed wallet must have zero balances");
    }
    this.version = version;
    this.lastAppliedSequenceNumber = lastAppliedSequenceNumber;
  }

  /** Creates an empty wallet with an explicitly assigned ledger account; emits one event. */
  public static Wallet create(WalletId id, AccountId accountId, UserId userId,
      Currency currency, Instant now) {
    Wallet wallet = new Wallet(id, accountId, userId, currency, WalletStatus.ACTIVE,
        Money.zero(currency), Money.zero(currency), 0L, -1L, now, now);
    wallet.registerEvent(WalletCreatedEvent.now(id, userId, currency, now));
    return wallet;
  }

  /** Restores validated persisted state without emitting creation or transition events. */
  public static Wallet restore(WalletId id, AccountId accountId, UserId userId, Currency currency,
      WalletStatus status, Money bookBalance, Money reservedBalance, long version,
      long lastAppliedSequenceNumber, Instant createdAt, Instant updatedAt) {
    return new Wallet(id, accountId, userId, currency, status, bookBalance, reservedBalance,
        version, lastAppliedSequenceNumber, createdAt, updatedAt);
  }

  /** Returns spendable funds; holds are already excluded from this amount. */
  public Money getAvailableBalance() {
    return bookBalance.subtract(reservedBalance);
  }

  /** Checks a strictly positive, same-currency debit against status and available funds. */
  public void assertCanDebit(Money amount) {
    requirePositiveAmount(amount);
    assertDebitEnabled();
    if (getAvailableBalance().isLessThan(amount)) {
      throw new InsufficientFundException("Insufficient available funds");
    }
  }

  /** Checks a strictly positive, same-currency credit against wallet status. */
  public void assertCanCredit(Money amount) {
    requirePositiveAmount(amount);
    if (status != WalletStatus.ACTIVE && status != WalletStatus.SUSPENDED_DEBIT) {
      throw new WalletFrozenException("Credits are not permitted in state: " + status);
    }
  }

  /** Reserves spendable funds; the application must create the matching Hold atomically. */
  public void reserveFunds(Money amount, Instant now) {
    validateMutationTime(now);
    assertCanDebit(amount);
      reservedBalance = reservedBalance.add(amount);
    updatedAt = now;
  }

  /** Releases a reservation even when frozen; the matching Hold must be resolved atomically. */
  public void unreserveFunds(Money amount, Instant now) {
    validateMutationTime(now);
    assertNotClosed();
    requirePositiveAmount(amount);
    if (reservedBalance.isLessThan(amount)) {
      throw new InvalidWalletStateException("Cannot release more than the reserved balance");
    }
      reservedBalance = reservedBalance.subtract(amount);
    updatedAt = now;
  }

  /**
   * Applies an ordinary posting after validating status, ownership, funds, time and order.
   *
   * <p>Entries must be applied in strictly increasing account sequence order. Gaps are
   * permitted; sequence allocation and duplicate prevention across processes require SQL
   * constraints and a lock or version check. A batch must first pass Transaction validation.
   */
  public void applyLedgerEntry(LedgerEntry entry, Instant now) {
    validateEntry(entry, now);
      bookBalance = switch (entry.entryType()) {
        case CREDIT -> {
          assertCanCredit(entry.amount());
          yield bookBalance.add(entry.amount());
        }
        case DEBIT -> {
          assertCanDebit(entry.amount());
          yield bookBalance.subtract(entry.amount());
        }
      };
    lastAppliedSequenceNumber = entry.sequenceNumber();
    updatedAt = now;
  }

  /**
   * Captures reserved funds with a debit, updating book and reserved balances together.
   *
   * <p>The command handler must validate the matching pending Hold, its wallet, exact
   * amount and expiry, then persist Hold, Wallet, balanced postings and outbox in one
   * transaction. This method alone does not authorize or identify a Hold.
   */
  public void captureReservedFunds(LedgerEntry debitEntry, Instant now) {
    validateEntry(debitEntry, now);
    assertDebitEnabled();
    if (debitEntry.entryType() != LedgerEntryType.DEBIT) {
      throw new InvalidWalletStateException("Capturing reserved funds requires a debit entry");
    }
    if (reservedBalance.isLessThan(debitEntry.amount())) {
      throw new InvalidWalletStateException("Capture exceeds the reserved balance");
    }
    Money nextBookBalance = bookBalance.subtract(debitEntry.amount());
    Money nextReservedBalance = reservedBalance.subtract(debitEntry.amount());
    bookBalance = nextBookBalance;
    reservedBalance = nextReservedBalance;
    lastAppliedSequenceNumber = debitEntry.sequenceNumber();
    updatedAt = now;
  }

  /** Freezes credits and debits; repeated freezing is a no-op after input validation. */
  public void suspend(String reason, Instant now) {
    changeStatus(WalletStatus.FROZEN, reason, now);
  }

  /** Suspends debits, preserving any existing credit restriction. */
  public void suspendDebits(String reason, Instant now) {
    WalletStatus target = status == WalletStatus.SUSPENDED_CREDIT || status == WalletStatus.FROZEN
        ? WalletStatus.FROZEN : WalletStatus.SUSPENDED_DEBIT;
    changeStatus(target, reason, now);
  }

  /** Suspends credits, preserving any existing debit restriction. */
  public void suspendCredits(String reason, Instant now) {
    WalletStatus target = status == WalletStatus.SUSPENDED_DEBIT || status == WalletStatus.FROZEN
        ? WalletStatus.FROZEN : WalletStatus.SUSPENDED_CREDIT;
    changeStatus(target, reason, now);
  }

  /** Reactivates a non-closed wallet; authorization belongs to the command boundary. */
  public void activate(Instant now) {
    changeStatus(WalletStatus.ACTIVE, "Reactivated", now);
  }

  /** Closes a wallet only when both balances are zero. Closure is terminal. */
  public void close(Instant now) {
    validateMutationTime(now);
    assertNotClosed();
    if (!bookBalance.isZero() || !reservedBalance.isZero()) {
      throw new InvalidWalletStateException("Wallet cannot close with funds or reservations");
    }
    WalletClosedEvent event = WalletClosedEvent.now(getId(), now);
    status = WalletStatus.CLOSED;
    updatedAt = now;
    registerEvent(event);
  }

  private void validateEntry(LedgerEntry entry, Instant now) {
    DomainValidation.requireNonNull(entry, "Ledger entry");
    validateMutationTime(now);
    if (!accountId.equals(entry.accountId())) {
      throw new InvalidWalletStateException("Ledger entry belongs to another account");
    }
    requireCurrency(entry.amount());
    if (entry.sequenceNumber() <= lastAppliedSequenceNumber) {
      throw new InvalidWalletStateException("Duplicate or out-of-order ledger entry");
    }
    if (entry.timestamp().isBefore(createdAt) || entry.timestamp().isAfter(now)) {
      throw new InvalidWalletStateException("Ledger entry timestamp is outside the allowed interval");
    }
  }

  private void requirePositiveAmount(Money amount) {
    DomainValidation.requireNonNull(amount, "Amount");
    requireCurrency(amount);
    amount.assertNonNegative();
    if (amount.isZero()) {
      throw new InvalidWalletStateException("Operation amount must be positive");
    }
  }

  private void requireCurrency(Money amount) {
    if (!currency.equals(amount.currency())) {
      throw new CurrencyMismatchException("Amount currency does not match wallet currency");
    }
  }

  private void validateMutationTime(Instant now) {
    DomainValidation.requireNonNull(now, "Operation time");
    if (now.isBefore(updatedAt)) {
      throw new InvalidWalletStateException("Operation time precedes the last update");
    }
  }

  private void assertDebitEnabled() {
    if (status != WalletStatus.ACTIVE && status != WalletStatus.SUSPENDED_CREDIT) {
      throw new WalletFrozenException("Debits are not permitted in state: " + status);
    }
  }

  private void assertNotClosed() {
    if (status == WalletStatus.CLOSED) {
      throw new InvalidWalletStateException("Wallet is permanently closed");
    }
  }

  private void changeStatus(WalletStatus target, String reason, Instant now) {
    validateMutationTime(now);
    assertNotClosed();
    DomainValidation.requireText(reason, "Status change reason");
    if (status == target) {
      return;
    }
    WalletStatusChangedEvent event =
        WalletStatusChangedEvent.now(getId(), status, target, reason, now);
    status = target;
    updatedAt = now;
    registerEvent(event);
  }

  /** Returns the explicit ledger account identity, not a conversion of the wallet identity. */
  public AccountId getAccountId() { return accountId; }
  /** Returns the external owner reference. */
  public UserId getUserId() { return userId; }
  /** Returns the wallet's single currency. */
  public Currency getCurrency() { return currency; }
  /** Returns the current operational state. */
  public WalletStatus getStatus() { return status; }
  /** Returns the book balance, including reserved funds. */
  public Money getBookBalance() { return bookBalance; }
  /** Returns the reserved part of the book balance. */
  public Money getReservedBalance() { return reservedBalance; }
  /** Returns the loaded version for a repository compare-and-update; not a mutation count. */
  public long getVersion() { return version; }
  /** Returns the last applied sequence, or -1 before any entry has been applied. */
  public long getLastAppliedSequenceNumber() { return lastAppliedSequenceNumber; }
  /** Returns the immutable creation timestamp. */
  public Instant getCreatedAt() { return createdAt; }
  /** Returns the time of the last effective state change. */
  public Instant getUpdatedAt() { return updatedAt; }
}
