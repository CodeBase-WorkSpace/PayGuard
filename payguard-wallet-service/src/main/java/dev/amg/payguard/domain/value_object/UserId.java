package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record UserId(UUID value) implements ValueObject {
  public UserId {
    DomainValidation.requireNonNull(value, "UserId value");
  }

  public static UserId generate() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId fromString(String uuidString) {
    return new UserId(UUID.fromString(uuidString));
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
