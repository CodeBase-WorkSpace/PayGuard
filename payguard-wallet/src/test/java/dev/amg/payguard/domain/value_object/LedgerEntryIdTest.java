package dev.amg.payguard.domain.value_object;

import domain.InvalidDomainArgumentException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the immutable identifier contract of {@link LedgerEntryId}. */
class LedgerEntryIdTest {

  private static final UUID FIXED_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

  /** Direct construction retains the exact UUID without transformation. */
  @Test
  void shouldPreserveUuid() {
    LedgerEntryId id = new LedgerEntryId(FIXED_ID);
    assertEquals(FIXED_ID, id.value());
  }

  /** Generated identifiers use a non-null random UUID, version 4. */
  @Test
  void shouldGenerateVersionFourIdentifier() {
    LedgerEntryId first = LedgerEntryId.generate();
    LedgerEntryId second = LedgerEntryId.generate();
    assertNotNull(first.value());
    assertEquals(4, first.value().version());
    assertNotEquals(first, second);
  }

  /** Canonical UUID strings round-trip through the identifier. */
  @Test
  void shouldParseCanonicalUuid() {
    assertEquals(new LedgerEntryId(FIXED_ID), LedgerEntryId.fromString(FIXED_ID.toString()));
  }

  /** Uppercase hexadecimal UUID input is allowed. */
  @Test
  void shouldAcceptUppercaseUuid() {
    assertEquals(new LedgerEntryId(FIXED_ID),
        LedgerEntryId.fromString(FIXED_ID.toString().toUpperCase(java.util.Locale.ROOT)));
  }

  /** The JDK's permissive, noncanonical UUID spellings are not accepted. */
  @Test
  void shouldRejectNoncanonicalUuid() {
    assertThrows(IllegalArgumentException.class, () -> LedgerEntryId.fromString("1-1-1-1-1"));
  }

  /** Malformed UUID strings must be rejected. */
  @Test
  void shouldRejectMalformedUuid() {
    assertThrows(IllegalArgumentException.class, () -> LedgerEntryId.fromString("not-a-uuid"));
  }

  /** Null UUIDs are rejected using the shared-domain validation contract. */
  @Test
  void shouldRejectNullUuid() {
    assertThrows(InvalidDomainArgumentException.class, () -> new LedgerEntryId(null));
  }

  /** Null UUID strings are rejected using the shared-domain validation contract. */
  @Test
  void shouldRejectNullString() {
    assertThrows(InvalidDomainArgumentException.class, () -> LedgerEntryId.fromString(null));
  }

  /** Record equality, hash code, and shared ValueObject equality must agree. */
  @Test
  void shouldPreserveValueEquality() {
    LedgerEntryId first = new LedgerEntryId(FIXED_ID);
    LedgerEntryId second = LedgerEntryId.fromString(FIXED_ID.toString());
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(first.sameValueAs(second));
    assertFalse(first.sameValueAs(null));
  }

  /** A different UUID must represent a different identifier. */
  @Test
  void shouldRejectDifferentUuidEquality() {
    assertNotEquals(new LedgerEntryId(FIXED_ID),
        new LedgerEntryId(UUID.fromString("123e4567-e89b-42d3-a456-426614174001")));
  }

  /** A different identifier type must never compare equal even with the same UUID. */
  @Test
  void shouldRejectDifferentIdentifierTypeEquality() {
    LedgerEntryId identifier = new LedgerEntryId(FIXED_ID);
    assertNotEquals(identifier, new WalletId(FIXED_ID));
  }
}
