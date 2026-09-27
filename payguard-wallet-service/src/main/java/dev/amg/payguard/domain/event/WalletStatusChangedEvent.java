package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.enums.WalletStatus;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record WalletStatusChangedEvent(
    UUID eventId,
    Instant occurredAt,
    WalletId walletId,
    WalletStatus previousStatus,
    WalletStatus newStatus,
    String reason)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static WalletStatusChangedEvent now(
      WalletId walletId, WalletStatus previous, WalletStatus current, String reason, Instant now) {
    return new WalletStatusChangedEvent(
        UUID.randomUUID(), now, walletId, previous, current, reason);
  }
}
