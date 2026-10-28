package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.TransactionId;
import dev.amg.payguard.domain.value_object.WalletId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record MoneyDepositedEvent(
    UUID eventId, Instant occurredAt, TransactionId transactionId, WalletId walletId, Money amount)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static MoneyDepositedEvent now(
      TransactionId transactionId, WalletId walletId, Money amount, Instant now) {
    return new MoneyDepositedEvent(UUID.randomUUID(), now, transactionId, walletId, amount);
  }
}
