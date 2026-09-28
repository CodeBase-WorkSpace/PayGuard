package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.LedgerEntryType;
import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.LedgerEntryId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.TransactionId;
import domain.DomainValidation;
import java.time.Instant;

public record LedgerEntry(
    LedgerEntryId id,
    TransactionId transactionId,
    AccountId accountId,
    Money amount,
    LedgerEntryType entryType,
    long sequenceNumber,
    Instant timestamp) {
  public LedgerEntry(
      LedgerEntryId id,
      TransactionId transactionId,
      AccountId accountId,
      Money amount,
      LedgerEntryType entryType,
      long sequenceNumber,
      Instant timestamp) {
    this.id = DomainValidation.requireNonNull(id, "Ledger entry id");
    this.transactionId = DomainValidation.requireNonNull(transactionId, "Transaction id");
    this.accountId = DomainValidation.requireNonNull(accountId, "Account id");
    this.amount = DomainValidation.requireNonNull(amount, "Amount");
    this.entryType = DomainValidation.requireNonNull(entryType, "Entry type");
    this.timestamp = DomainValidation.requireNonNull(timestamp, "Timestamp");

    amount.assertNonNegative();

    if (sequenceNumber < 0) {
      throw new dev.amg.payguard.domain.exception.InvalidWalletStateException(
          "Sequence number cannot be negative");
    }
    this.sequenceNumber = sequenceNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LedgerEntry that)) return false;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
