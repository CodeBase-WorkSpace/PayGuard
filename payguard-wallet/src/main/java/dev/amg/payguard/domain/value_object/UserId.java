package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

/**
 * Immutable, strongly typed identifier for a user in the Identity bounded context.
 *
 * @param value the UUID identity value
 */
public record UserId(UUID value) implements ValueObject {

  /** Rejects a null identity. */
  public UserId {
    DomainValidation.requireNonNull(value, "UserId value");
  }

  /**
   * Parses a canonical UUID string; uppercase hex digits are accepted.
   *
   * @param value the canonical UUID string
   * @return the corresponding UserId
   * @throws IllegalArgumentException for malformed or noncanonical UUIDs
   */
  public static UserId fromString(String value) {
    DomainValidation.requireNonNull(value, "UserId string");
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("Noncanonical UserId UUID: " + value);
    }
    return new UserId(parsed);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
