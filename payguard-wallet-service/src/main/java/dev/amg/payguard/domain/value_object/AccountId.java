package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record AccountId(UUID value) implements ValueObject {
  public AccountId {
    DomainValidation.requireNonNull(value, "AccountId value");
  }

  public static AccountId generate() {
    return new AccountId(UUID.randomUUID());
  }

  public static AccountId fromString(String uuidString) {
    return new AccountId(UUID.fromString(uuidString));
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
