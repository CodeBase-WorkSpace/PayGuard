package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.InvalidIdempotencyKeyException;
import domain.DomainValidation;
import domain.ValueObject;
import java.time.Instant;

public record IdempotencyKey(String value, Instant createdAt) implements ValueObject {
  public IdempotencyKey {
    DomainValidation.requireNonNull(value, "Idempotency key value");
    DomainValidation.requireNonNull(createdAt, "CreatedAt");
    if (value.isBlank()) {
      throw new InvalidIdempotencyKeyException("Idempotency key cannot be blank");
    }
  }

  public static IdempotencyKey of(String value, Instant now) {
    return new IdempotencyKey(value, now);
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
