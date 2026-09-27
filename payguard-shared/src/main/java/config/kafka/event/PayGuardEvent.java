package config.kafka.event;

import domain.DomainValidation;
import exception.InvalidKafkaEventException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PayGuardEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String idempotencyKey,
    Instant occurredAt,
    String producer,
    String correlationId,
    String causationId,
    int schemaVersion,
    T payload,
    Map<String, String> metadata) {

  public PayGuardEvent {
    DomainValidation.requireNonNull(eventId, "eventId");
    requireText(eventType, "eventType must not be blank");
    requireText(aggregateType, "aggregateType must not be blank");
    requireText(aggregateId, "aggregateId must not be blank");
    requireText(idempotencyKey, "idempotencyKey must not be blank");
    DomainValidation.requireNonNull(occurredAt, "occurredAt");
    requireText(producer, "producer must not be blank");
    DomainValidation.requireNonNull(payload, "payload");
    if (schemaVersion <= 0) {
      throw new InvalidKafkaEventException("schemaVersion must be positive");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static <T extends EventPayload> PayGuardEvent<T> create(
      String eventType,
      String aggregateType,
      String aggregateId,
      String idempotencyKey,
      String producer,
      String correlationId,
      String causationId,
      T payload) {
    return new PayGuardEvent<>(
        UUID.randomUUID(),
        eventType,
        aggregateType,
        aggregateId,
        idempotencyKey,
        Instant.now(),
        producer,
        correlationId,
        causationId,
        1,
        payload,
        Map.of());
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new InvalidKafkaEventException(message);
    }
  }
}
