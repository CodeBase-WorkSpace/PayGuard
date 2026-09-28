package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record TransactionId(UUID value) implements ValueObject {
  public TransactionId {
    DomainValidation.requireNonNull(value, "TransactionId value");
  }

  public static TransactionId generate() {
    return new TransactionId(UUID.randomUUID());
  }

  public static TransactionId fromString(String uuidString) {
    return new TransactionId(UUID.fromString(uuidString));
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
