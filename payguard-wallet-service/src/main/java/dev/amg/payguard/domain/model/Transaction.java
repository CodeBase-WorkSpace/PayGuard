package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.TransactionStatus;
import dev.amg.payguard.domain.enums.TransactionType;
import dev.amg.payguard.domain.exception.InvalidWalletStateException;
import dev.amg.payguard.domain.exception.TransactionAlreadySettledException;
import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.IdempotencyKey;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.TransactionId;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;
import java.time.Instant;
import java.util.Optional;

public class Transaction extends AggregateRoot<TransactionId, DomainEvent> {
  private final TransactionId id;
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

  public Transaction(
      TransactionId id,
      IdempotencyKey idempotencyKey,
      TransactionType type,
      AccountId sourceAccountId,
      AccountId destinationAccountId,
      Money totalAmount,
      Money feeAmount,
      TransactionStatus status,
      String failureReason,
      Instant createdAt,
      Instant settledAt) {
    super(id);
    this.id = DomainValidation.requireNonNull(id, "Transaction id");
    this.idempotencyKey = DomainValidation.requireNonNull(idempotencyKey, "Idempotency key");
    this.type = DomainValidation.requireNonNull(type, "Transaction type");
    this.sourceAccountId = DomainValidation.requireNonNull(sourceAccountId, "Source account id");
    this.destinationAccountId =
        DomainValidation.requireNonNull(destinationAccountId, "Destination account id");
    this.totalAmount = DomainValidation.requireNonNull(totalAmount, "Total amount");
    this.feeAmount = DomainValidation.requireNonNull(feeAmount, "Fee amount");
    this.status = DomainValidation.requireNonNull(status, "Transaction status");
    this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
    this.failureReason = failureReason;
    this.settledAt = settledAt;

    totalAmount.assertNonNegative();
    feeAmount.assertNonNegative();
  }

  public static Transaction initiate(
      TransactionId id,
      IdempotencyKey idempotencyKey,
      TransactionType type,
      AccountId sourceAccountId,
      AccountId destinationAccountId,
      Money totalAmount,
      Money feeAmount,
      Instant now) {
    return new Transaction(
        id,
        idempotencyKey,
        type,
        sourceAccountId,
        destinationAccountId,
        totalAmount,
        feeAmount,
        TransactionStatus.INITIATED,
        null,
        now,
        null);
  }

  public void markSettled(Instant now) {
    if (this.status == TransactionStatus.SUCCESSFUL) {
      throw new TransactionAlreadySettledException(
          "Transaction has already been settled successfully: " + this.id);
    }
    if (this.status == TransactionStatus.FAILED) {
      throw new InvalidWalletStateException("Cannot settle a failed transaction: " + this.id);
    }
    this.status = TransactionStatus.SUCCESSFUL;
    this.settledAt = DomainValidation.requireNonNull(now, "Settlement time");
  }

  public void markFailed(String reason) {
    if (this.status == TransactionStatus.SUCCESSFUL) {
      throw new TransactionAlreadySettledException(
          "Cannot fail an already settled transaction: " + this.id);
    }
    this.status = TransactionStatus.FAILED;
    this.failureReason = DomainValidation.requireText(reason, "Failure reason");
  }

  public void reverse() {
    if (this.status != TransactionStatus.SUCCESSFUL) {
      throw new InvalidWalletStateException("Only successful transactions can be reversed");
    }
    this.status = TransactionStatus.REVERSED;
  }

  @Override
  public TransactionId getId() {
    return id;
  }

  public IdempotencyKey getIdempotencyKey() {
    return idempotencyKey;
  }

  public TransactionType getType() {
    return type;
  }

  public AccountId getSourceAccountId() {
    return sourceAccountId;
  }

  public AccountId getDestinationAccountId() {
    return destinationAccountId;
  }

  public Money getTotalAmount() {
    return totalAmount;
  }

  public Money getFeeAmount() {
    return feeAmount;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public Optional<String> getFailureReason() {
    return Optional.ofNullable(failureReason);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Optional<Instant> getSettledAt() {
    return Optional.ofNullable(settledAt);
  }
}
