package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record FundsHoldCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    HoldId holdId,
    WalletId walletId,
    Money amount,
    Instant expiresAt)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static FundsHoldCreatedEvent now(
      HoldId holdId, WalletId walletId, Money amount, Instant expiresAt, Instant now) {
    return new FundsHoldCreatedEvent(UUID.randomUUID(), now, holdId, walletId, amount, expiresAt);
  }
}
