package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record WalletClosedEvent(UUID eventId, Instant occurredAt, WalletId walletId)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static WalletClosedEvent now(WalletId walletId, Instant now) {
    return new WalletClosedEvent(UUID.randomUUID(), now, walletId);
  }
}
