package dev.amg.payguard.domain.event;

import dev.amg.payguard.domain.value_object.AccountId;
import dev.amg.payguard.domain.value_object.Money;
import dev.amg.payguard.domain.value_object.TransactionId;
import domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
    UUID eventId,
    Instant occurredAt,
    TransactionId transactionId,
    AccountId sourceAccountId,
    AccountId destinationAccountId,
    Money amount,
    Money fee)
    implements DomainEvent {
  @Override
  public Instant occurredOn() {
    return occurredAt;
  }

  public static TransferCompletedEvent now(
      TransactionId transactionId,
      AccountId source,
      AccountId destination,
      Money amount,
      Money fee,
      Instant now) {
    return new TransferCompletedEvent(
        UUID.randomUUID(), now, transactionId, source, destination, amount, fee);
  }
}
