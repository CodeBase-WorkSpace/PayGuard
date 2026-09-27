package dev.amg.payguard.domain.model;

import dev.amg.payguard.domain.enums.HoldStatus;
import dev.amg.payguard.domain.event.FundsHoldCapturedEvent;
import dev.amg.payguard.domain.event.FundsHoldExpiredEvent;
import dev.amg.payguard.domain.event.FundsHoldReleasedEvent;
import dev.amg.payguard.domain.exception.HoldExpiredException;
import dev.amg.payguard.domain.exception.InvalidHoldStateException;
import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.AggregateRoot;
import domain.DomainEvent;
import domain.DomainValidation;
import java.time.Instant;

public class Hold extends AggregateRoot<HoldId, DomainEvent> {
  private final HoldId id;
  private final WalletId walletId;
  private final Money amount;
  private final String reason;
  private final String referenceId;
  private final Instant expiresAt;
  private final Instant createdAt;
  private HoldStatus status;

  public Hold(
      HoldId id,
      WalletId walletId,
      Money amount,
      String reason,
      String referenceId,
      Instant expiresAt,
      Instant createdAt,
      HoldStatus status) {
    super(id);
    this.id = DomainValidation.requireNonNull(id, "Hold id");
    this.walletId = DomainValidation.requireNonNull(walletId, "Wallet id");
    this.amount = DomainValidation.requireNonNull(amount, "Amount");
    this.reason = DomainValidation.requireText(reason, "Reason");
    this.referenceId = DomainValidation.requireText(referenceId, "Reference id");
    this.expiresAt = DomainValidation.requireNonNull(expiresAt, "Expiration time");
    this.createdAt = DomainValidation.requireNonNull(createdAt, "Creation time");
    this.status = DomainValidation.requireNonNull(status, "Hold status");

    amount.assertNonNegative();
  }

  public static Hold createNew(
      HoldId id,
      WalletId walletId,
      Money amount,
      String reason,
      String referenceId,
      Instant expiresAt,
      Instant now) {
    if (expiresAt.isBefore(now)) {
      throw new InvalidHoldStateException("Expiration time must be in the future");
    }
    return new Hold(id, walletId, amount, reason, referenceId, expiresAt, now, HoldStatus.PENDING);
  }

  public void capture(Instant now) {
    assertPending();
    if (isExpired(now)) {
      this.status = HoldStatus.EXPIRED;
      registerEvent(FundsHoldExpiredEvent.now(this.id, this.walletId, this.amount, now));
      throw new HoldExpiredException("Cannot capture hold: Hold is already expired");
    }
    this.status = HoldStatus.CAPTURED;
    registerEvent(FundsHoldCapturedEvent.now(this.id, this.walletId, this.amount, now));
  }

  public void release(Instant now) {
    assertPending();
    this.status = HoldStatus.RELEASED;
    registerEvent(FundsHoldReleasedEvent.now(this.id, this.walletId, this.amount, now));
  }

  public void expire(Instant now) {
    assertPending();
    if (!isExpired(now)) {
      throw new InvalidHoldStateException(
          "Cannot expire hold: Expiration time has not elapsed yet");
    }
    this.status = HoldStatus.EXPIRED;
    registerEvent(FundsHoldExpiredEvent.now(this.id, this.walletId, this.amount, now));
  }

  public boolean isExpired(Instant now) {
    return now.isAfter(this.expiresAt);
  }

  private void assertPending() {
    if (this.status != HoldStatus.PENDING) {
      throw new InvalidHoldStateException("Hold is not in PENDING state. Current: " + this.status);
    }
  }

  @Override
  public HoldId getId() {
    return id;
  }

  public WalletId getWalletId() {
    return walletId;
  }

  public Money getAmount() {
    return amount;
  }

  public String getReason() {
    return reason;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public HoldStatus getStatus() {
    return status;
  }
}
