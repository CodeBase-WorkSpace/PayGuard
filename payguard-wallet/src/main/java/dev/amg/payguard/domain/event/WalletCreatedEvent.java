package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.UserId;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record WalletCreatedEvent(
    UUID eventId, Instant occurredAt, WalletId walletId, UserId userId, Currency currency)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static WalletCreatedEvent now(
      WalletId walletId, UserId userId, Currency currency, Instant now) {
    return new WalletCreatedEvent(UUID.randomUUID(), now, walletId, userId, currency);
  }
}
