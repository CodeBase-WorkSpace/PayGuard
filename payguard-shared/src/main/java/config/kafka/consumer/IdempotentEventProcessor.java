package config.kafka.consumer;

import config.kafka.event.EventPayload;
import config.kafka.event.PayGuardEvent;
import domain.DomainValidation;

/** Coordinates event deduplication with the consumer's local transaction. */
public final class IdempotentEventProcessor {
  private final ProcessedEventStore eventStore;

  public IdempotentEventProcessor(ProcessedEventStore eventStore) {
    this.eventStore = DomainValidation.requireNonNull(eventStore, "eventStore");
  }

  /** Returns true when the handler ran, or false when the event was already processed. */
  public boolean process(
      PayGuardEvent<? extends EventPayload> event, String consumerName, Runnable handler) {
    DomainValidation.requireNonNull(event, "event");
    DomainValidation.requireNonNull(handler, "handler");
    if (consumerName == null || consumerName.isBlank()) {
      throw new exception.InvalidKafkaEventException("consumerName must not be null or blank");
    }
    if (!eventStore.tryMarkProcessed(event.eventId(), consumerName)) {
      return false;
    }
    handler.run();
    return true;
  }
}
