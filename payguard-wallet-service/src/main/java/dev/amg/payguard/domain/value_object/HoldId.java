package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record HoldId(UUID value) implements ValueObject {
  public HoldId {
    DomainValidation.requireNonNull(value, "HoldId value");
  }

  public static HoldId generate() {
    return new HoldId(UUID.randomUUID());
  }

  public static HoldId fromString(String uuidString) {
    return new HoldId(UUID.fromString(uuidString));
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
