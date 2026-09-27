package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record FundsHoldExpiredEvent(
    UUID eventId, Instant occurredAt, HoldId holdId, WalletId walletId, Money amount)
    implements DomainEvent {
  public static FundsHoldExpiredEvent now(
      HoldId holdId, WalletId walletId, Money amount, Instant now) {
    return new FundsHoldExpiredEvent(UUID.randomUUID(), now, holdId, walletId, amount);
  }

  @Override
  public Instant occurredOn() {
    return occurredAt;
  }
}
