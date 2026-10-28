package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.InvalidIdempotencyKeyException;
import domain.DomainValidation;
import domain.ValueObject;
import java.util.regex.Pattern;

/**
 * An exact, case-sensitive, client-supplied idempotency key.
 *
 * <p>The protocol for this Wallet module accepts 1 to 255 ASCII characters from
 * A-Z, a-z, 0-9, period, underscore, tilde and hyphen. Keys are never trimmed,
 * case-folded or otherwise normalized.
 *
 * <p>Tenant/operation scope, request fingerprint, status, and timestamps belong
 * to the persisted idempotency record, not to this value object.
 *
 * @param value the exact idempotency key
 */
public record IdempotencyKey(String value) implements ValueObject {

  /** Maximum key length, chosen as an explicit Wallet API policy. */
  public static final int MAX_LENGTH = 255;

  private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._~-]{1,255}");

  /** Validates that the key is a permitted immutable protocol value. */
  public IdempotencyKey {
    DomainValidation.requireNonNull(value, "Idempotency key value");
    if (!VALID_KEY.matcher(value).matches()) {
      throw new InvalidIdempotencyKeyException(
          "Idempotency key must be 1-255 ASCII letters, digits, '.', '_', '~' or '-'");
    }
  }

  /** Creates a key from an exact, client-supplied string. */
  public static IdempotencyKey of(String value) {
    return new IdempotencyKey(value);
  }

  /** Compares value objects using their exact record values and types. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }
}
