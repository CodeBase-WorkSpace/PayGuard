package dev.amg.payguard.domain.value_object;

import domain.DomainValidation;
import domain.ValueObject;
import java.util.UUID;

/**
 * Immutable, strongly typed identifier for a wallet aggregate.
 *
 * @param value the UUID identity value
 */
public record WalletId(UUID value) implements ValueObject {

  /** Rejects a null identity. */
  public WalletId {
    DomainValidation.requireNonNull(value, "WalletId value");
  }

  /** Generates a new random (version 4) UUID identifier. */
  public static WalletId generate() {
    return new WalletId(UUID.randomUUID());
  }

  /**
   * Parses a canonical UUID string; uppercase hex digits are accepted.
   *
   * @param value the canonical UUID string
   * @return the corresponding WalletId
   * @throws IllegalArgumentException for malformed or noncanonical UUIDs
   */
  public static WalletId fromString(String value) {
    DomainValidation.requireNonNull(value, "WalletId string");
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equalsIgnoreCase(value)) {
      throw new IllegalArgumentException("Noncanonical WalletId UUID: " + value);
    }
    return new WalletId(parsed);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
