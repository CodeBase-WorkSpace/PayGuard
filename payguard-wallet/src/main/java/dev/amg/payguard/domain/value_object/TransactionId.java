package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

/**
 * Immutable, strongly typed identifier for a financial transaction.
 *
 * @param value the UUID identity value
 */
public record TransactionId(UUID value) implements ValueObject {

  /** Rejects a null identity. */
  public TransactionId {
    DomainValidation.requireNonNull(value, "TransactionId value");
  }

  /** Generates a new random (version 4) UUID identifier. */
  public static TransactionId generate() {
    return new TransactionId(UUID.randomUUID());
  }

  /**
   * Parses a canonical UUID string; uppercase hex digits are accepted.
   *
   * @param value the canonical UUID string
   * @return the corresponding TransactionId
   * @throws IllegalArgumentException for malformed or noncanonical UUIDs
   */
  public static TransactionId fromString(String value) {
    DomainValidation.requireNonNull(value, "TransactionId string");
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("Noncanonical TransactionId UUID: " + value);
    }
    return new TransactionId(parsed);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
