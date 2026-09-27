package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

public record LedgerEntryId(UUID value) implements ValueObject {
  public LedgerEntryId {
    DomainValidation.requireNonNull(value, "LedgerEntryId value");
  }

  public static LedgerEntryId generate() {
    return new LedgerEntryId(UUID.randomUUID());
  }

  public static LedgerEntryId fromString(String uuidString) {
    return new LedgerEntryId(UUID.fromString(uuidString));
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
