package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.InvalidIdempotencyKeyException;
import domain.InvalidDomainArgumentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests exact protocol validation and equality of idempotency keys. */
class IdempotencyKeyTest {

  /** A normal client-provided key is preserved verbatim. */
  @Test
  void shouldPreserveValidKey() {
    assertEquals("order-42_v2~A", IdempotencyKey.of("order-42_v2~A").value());
  }

  /** A one-character key is valid. */
  @Test
  void shouldAcceptOneCharacterKey() {
    assertEquals("a", IdempotencyKey.of("a").value());
  }

  /** A key at the maximum supported length is valid. */
  @Test
  void shouldAcceptMaximumLength() {
    assertEquals(255, IdempotencyKey.of("a".repeat(255)).value().length());
  }

  /** A key longer than the protocol limit must be rejected. */
  @Test
  void shouldRejectExcessiveLength() {
    assertThrows(InvalidIdempotencyKeyException.class,
        () -> IdempotencyKey.of("a".repeat(256)));
  }

  /** Empty strings are not usable idempotency keys. */
  @Test
  void shouldRejectEmptyString() {
    assertThrows(InvalidIdempotencyKeyException.class, () -> IdempotencyKey.of(""));
  }

  /** Whitespace-only strings are not usable idempotency keys. */
  @Test
  void shouldRejectBlankString() {
    assertThrows(InvalidIdempotencyKeyException.class, () -> IdempotencyKey.of("   "));
  }

  /** Leading and trailing whitespace is rejected rather than trimmed. */
  @Test
  void shouldRejectWhitespaceWithoutNormalization() {
    assertThrows(InvalidIdempotencyKeyException.class,
        () -> IdempotencyKey.of(" abc "));
  }

  /** Control characters are not valid in a key used in an HTTP header. */
  @Test
  void shouldRejectControlCharacters() {
    assertThrows(InvalidIdempotencyKeyException.class,
        () -> IdempotencyKey.of("a\nb"));
  }

  /** Non-ASCII characters are rejected by the explicit Wallet API policy. */
  @Test
  void shouldRejectNonAscii() {
    assertThrows(InvalidIdempotencyKeyException.class,
        () -> IdempotencyKey.of("caf\u00e9"));
  }

  /** Printable separators outside the allowed alphabet are rejected. */
  @Test
  void shouldRejectDisallowedPunctuation() {
    assertThrows(InvalidIdempotencyKeyException.class,
        () -> IdempotencyKey.of("order/42"));
  }

  /** Case is significant for idempotency semantics. */
  @Test
  void shouldPreserveCaseSensitiveEquality() {
    assertNotEquals(IdempotencyKey.of("Order-42"), IdempotencyKey.of("order-42"));
  }

  /** Equal keys have stable record equality and hash codes. */
  @Test
  void shouldCompareEqualKeysByValue() {
    IdempotencyKey first = IdempotencyKey.of("checkout-123");
    IdempotencyKey second = IdempotencyKey.of("checkout-123");
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(first.sameValueAs(second));
    assertFalse(first.sameValueAs(null));
  }

  /** Null inputs are rejected by the shared-domain validation contract. */
  @Test
  void shouldRejectNullKey() {
    assertThrows(InvalidDomainArgumentException.class, () -> IdempotencyKey.of(null));
  }

  /** Different values are not interchangeable idempotency keys. */
  @Test
  void shouldRejectDifferentKeyEquality() {
    assertNotEquals(IdempotencyKey.of("order-1"), IdempotencyKey.of("order-2"));
  }
}
