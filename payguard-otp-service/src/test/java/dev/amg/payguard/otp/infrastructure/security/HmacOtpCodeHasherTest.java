package dev.amg.payguard.otp.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HmacOtpCodeHasherTest {

  private final HmacOtpCodeHasher hasher =
      new HmacOtpCodeHasher("a-secure-test-pepper-with-at-least-32-chars");

  @Test
  void storesSaltedHashAndMatchesOnlyOriginalValue() {
    String encoded = hasher.hash("123456");

    assertNotEquals("123456", encoded);
    assertTrue(hasher.matches("123456", encoded));
    assertFalse(hasher.matches("123457", encoded));
    assertFalse(hasher.matches("123456", "not-a-hash"));
  }
}
