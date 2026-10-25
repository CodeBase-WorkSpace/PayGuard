package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record FundsHoldReleasedEvent(
    UUID eventId, Instant occurredAt, HoldId holdId, WalletId walletId, Money amount)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static FundsHoldReleasedEvent now(
      HoldId holdId, WalletId walletId, Money amount, Instant now) {
    return new FundsHoldReleasedEvent(UUID.randomUUID(), now, holdId, walletId, amount);
  }
}
