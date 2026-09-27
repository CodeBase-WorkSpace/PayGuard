package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.LedgerEntryType;
import dev.amg.payguard.domain.enums.WalletStatus;
import dev.amg.payguard.domain.event.WalletClosedEvent;
import dev.amg.payguard.domain.event.WalletCreatedEvent;
import dev.amg.payguard.domain.event.WalletStatusChangedEvent;
import dev.amg.payguard.domain.exception.InsufficientFundException;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.exception.WalletFrozenException;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.UserId;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;
import java.time.Instant;
import java.util.Currency;

public class Wallet extends AggregateRoot<WalletId, DomainEvent> {
  private final WalletId id;
  private final UserId userId;
  private final Currency currency;
  private final Instant createdAt;
  private WalletStatus status;
  private Money bookBalance;
  private Money reservedBalance;
  private long version;
  private Instant updatedAt;

  public Wallet(
      WalletId id,
      UserId userId,
      Currency currency,
      WalletStatus status,
      Money bookBalance,
      Money reservedBalance,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    super(id);
    this.id = DomainValidation.requireNonNull(id, "Wallet id");
    this.userId = DomainValidation.requireNonNull(userId, "User id");
    this.currency = DomainValidation.requireNonNull(currency, "Currency");
    this.status = DomainValidation.requireNonNull(status, "Wallet status");
    this.bookBalance = DomainValidation.requireNonNull(bookBalance, "Book balance");
    this.reservedBalance = DomainValidation.requireNonNull(reservedBalance, "Reserved balance");
    this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
    this.updatedAt = DomainValidation.requireNonNull(updatedAt, "Update time");
    this.version = version;

    reservedBalance.assertNonNegative();
  }

  public static Wallet create(WalletId id, UserId userId, Currency currency, Instant now) {
    Wallet wallet =
        new Wallet(
            id,
            userId,
            currency,
            WalletStatus.ACTIVE,
            Money.zero(currency),
            Money.zero(currency),
            0L,
            now,
            now);
    wallet.registerEvent(WalletCreatedEvent.now(id, userId, currency, now));
    return wallet;
  }

  public Money getAvailableBalance() {
    return this.bookBalance.subtract(this.reservedBalance);
  }

  public void assertCanDebit(Money amount) {
    assertActive();
    if (this.status == WalletStatus.SUSPENDED_DEBIT) {
      throw new WalletFrozenException("Debits are currently suspended for wallet: " + this.id);
    }
    amount.assertNonNegative();
    if (getAvailableBalance().isLessThan(amount)) {
      throw new InsufficientFundException(
          String.format(
              "Insufficient funds in wallet: %s. Available: %s, Requested: %s",
              this.id.value(), getAvailableBalance().amount(), amount.amount()));
    }
  }

  public void assertCanCredit(Money amount) {
    assertActive();
    if (this.status == WalletStatus.SUSPENDED_CREDIT) {
      throw new WalletFrozenException("Credits are currently suspended for wallet: " + this.id);
    }
    amount.assertNonNegative();
  }

  public void reserveFunds(Money amount, Instant now) {
    assertCanDebit(amount);
    this.reservedBalance = this.reservedBalance.add(amount);
    this.updatedAt = now;
  }

  public void unreserveFunds(Money amount, Instant now) {
    amount.assertNonNegative();
    if (this.reservedBalance.isLessThan(amount)) {
      throw new InvalidWalletStateException(
          "Cannot unreserve more funds than are currently reserved");
    }
    this.reservedBalance = this.reservedBalance.subtract(amount);
    this.updatedAt = now;
  }

  public void applyLedgerEntry(LedgerEntry entry, Instant now) {
    DomainValidation.requireNonNull(entry, "Ledger entry");
    if (!entry.accountId().value().equals(this.id.value())) {
      throw new InvalidWalletStateException("Ledger entry account mismatch");
    }

    if (entry.entryType() == LedgerEntryType.CREDIT) {
      this.bookBalance = this.bookBalance.add(entry.amount());
    } else if (entry.entryType() == LedgerEntryType.DEBIT) {
      this.bookBalance = this.bookBalance.subtract(entry.amount());
    }
    this.updatedAt = now;
  }

  public void suspend(String reason, Instant now) {
    assertNotClosed();
    WalletStatus previous = this.status;
    this.status = WalletStatus.FROZEN;
    this.updatedAt = now;
    registerEvent(WalletStatusChangedEvent.now(this.id, previous, this.status, reason, now));
  }

  public void activate(Instant now) {
    assertNotClosed();
    WalletStatus previous = this.status;
    this.status = WalletStatus.ACTIVE;
    this.updatedAt = now;
    registerEvent(WalletStatusChangedEvent.now(this.id, previous, this.status, "Reactivated", now));
  }

  public void close(Instant now) {
    assertNotClosed();
    if (!this.bookBalance.isZero()) {
      throw new InvalidWalletStateException(
          "Wallet cannot be closed while book balance is non-zero: " + this.bookBalance);
    }
    if (!this.reservedBalance.isZero()) {
      throw new InvalidWalletStateException(
          "Wallet cannot be closed while funds are on hold: " + this.reservedBalance);
    }
    this.status = WalletStatus.CLOSED;
    this.updatedAt = now;
    registerEvent(WalletClosedEvent.now(this.id, now));
  }

  private void assertActive() {
    if (this.status == WalletStatus.FROZEN || this.status == WalletStatus.CLOSED) {
      throw new WalletFrozenException("Wallet is in inactive state: " + this.status);
    }
  }

  private void assertNotClosed() {
    if (this.status == WalletStatus.CLOSED) {
      throw new InvalidWalletStateException("Wallet is permanently closed: " + this.id);
    }
  }

  @Override
  public WalletId getId() {
    return id;
  }

  public UserId getUserId() {
    return userId;
  }

  public Currency getCurrency() {
    return currency;
  }

  public WalletStatus getStatus() {
    return status;
  }

  public Money getBookBalance() {
    return bookBalance;
  }

  public Money getReservedBalance() {
    return reservedBalance;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
