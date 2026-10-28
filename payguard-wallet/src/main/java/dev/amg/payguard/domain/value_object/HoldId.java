package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

/**
 * Immutable, strongly typed identifier for a reservation of funds.
 *
 * @param value the UUID identity value
 */
public record HoldId(UUID value) implements ValueObject {

  /** Rejects a null identity. */
  public HoldId {
    DomainValidation.requireNonNull(value, "HoldId value");
  }

  /** Generates a new random (version 4) UUID identifier. */
  public static HoldId generate() {
    return new HoldId(UUID.randomUUID());
  }

  /**
   * Parses a canonical UUID string; uppercase hex digits are accepted.
   *
   * @param value the canonical UUID string
   * @return the corresponding HoldId
   * @throws IllegalArgumentException for malformed or noncanonical UUIDs
   */
  public static HoldId fromString(String value) {
    DomainValidation.requireNonNull(value, "HoldId string");
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("Noncanonical HoldId UUID: " + value);
    }
    return new HoldId(parsed);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
