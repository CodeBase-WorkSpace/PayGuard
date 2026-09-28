package dev.amg.payguard.otp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpChallengeTest {

  private final OtpCodeHasher hasher =
      new OtpCodeHasher() {
        @Override
        public String hash(String value) {
          return "hash:" + value;
        }

        @Override
        public boolean matches(String value, String encodedHash) {
          return encodedHash.equals(hash(value));
        }
      };

  @Test
  void successfulVerificationIsSingleUse() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    OtpChallenge challenge = challenge(now, 5);

    assertEquals(
        OtpVerificationResult.VERIFIED,
        challenge.verify(OtpPurpose.LOGIN, "123456", null, hasher, false, now));
    assertEquals(
        OtpVerificationResult.ALREADY_USED,
        challenge.verify(OtpPurpose.LOGIN, "123456", null, hasher, false, now.plusSeconds(1)));
  }

  @Test
  void fifthInvalidAttemptLocksChallenge() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    OtpChallenge challenge = challenge(now, 2);

    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        challenge.verify(OtpPurpose.LOGIN, "000000", null, hasher, false, now));
    assertEquals(
        OtpVerificationResult.LOCKED,
        challenge.verify(OtpPurpose.LOGIN, "000000", null, hasher, false, now));
    assertEquals(
        OtpVerificationResult.LOCKED,
        challenge.verify(OtpPurpose.LOGIN, "123456", null, hasher, false, now));
  }

  @Test
  void expiredChallengeCannotBeVerified() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    OtpChallenge challenge = challenge(now, 5);

    assertEquals(
        OtpVerificationResult.EXPIRED,
        challenge.verify(OtpPurpose.LOGIN, "123456", null, hasher, false, now.plusSeconds(300)));
  }

  @Test
  void deviceBindingIsRequiredWhenConfigured() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    OtpChallenge challenge =
        OtpChallenge.issue(
            UUID.randomUUID(),
            "user-1",
            OtpPurpose.LOGIN,
            OtpChannel.SMS,
            OtpVerificationMode.RANDOM_NUMERIC,
            hasher.hash("123456"),
            hasher.hash("device-a"),
            5,
            now,
            now.plusSeconds(300),
            now.plusSeconds(86_400));

    assertEquals(
        OtpVerificationResult.DEVICE_MISMATCH,
        challenge.verify(OtpPurpose.LOGIN, "123456", "device-b", hasher, false, now));
    assertEquals(
        OtpVerificationResult.VERIFIED,
        challenge.verify(OtpPurpose.LOGIN, "123456", "device-a", hasher, false, now));
  }

  private static OtpChallenge challenge(Instant now, int maxAttempts) {
    return OtpChallenge.issue(
        UUID.randomUUID(),
        "user-1",
        OtpPurpose.LOGIN,
        OtpChannel.SMS,
        OtpVerificationMode.RANDOM_NUMERIC,
        "hash:123456",
        null,
        maxAttempts,
        now,
        now.plusSeconds(300),
        now.plusSeconds(86_400));
  }
}
