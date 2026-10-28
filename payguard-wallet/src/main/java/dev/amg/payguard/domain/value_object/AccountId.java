package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

/**
 * Immutable, strongly typed identifier for a ledger account.
 *
 * @param value the UUID identity value
 */
public record AccountId(UUID value) implements ValueObject {

  /** Rejects a null identity. */
  public AccountId {
    DomainValidation.requireNonNull(value, "AccountId value");
  }

  /** Generates a new random (version 4) UUID identifier. */
  public static AccountId generate() {
    return new AccountId(UUID.randomUUID());
  }

  /**
   * Parses a canonical UUID string; uppercase hex digits are accepted.
   *
   * @param value the canonical UUID string
   * @return the corresponding AccountId
   * @throws IllegalArgumentException for malformed or noncanonical UUIDs
   */
  public static AccountId fromString(String value) {
    DomainValidation.requireNonNull(value, "AccountId string");
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("Noncanonical AccountId UUID: " + value);
    }
    return new AccountId(parsed);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
