package dev.amg.payguard.identity.verification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OTPDomainContractTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRES = NOW.plusSeconds(300);
  private static final Instant RETAINS = EXPIRES.atZone(ZoneOffset.UTC).plusYears(3).toInstant();
  private static final OtpId ID = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
  private static final CustomerId CUSTOMER = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");
  private static final OtpCodeHasher HASHER =
      new OtpCodeHasher() {
        @Override
        public String hash(String value) {
          return "hash:" + value;
        }

        @Override
        public boolean matches(String value, String encodedHash) {
          return hash(value).equals(encodedHash);
        }
      };

  @Test
  void issueRequiresActiveZeroAttemptSnapshot() {
    assertEquals(
        OtpStatus.ACTIVE,
        OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null)).getSnapshot().status());
    assertThrows(
        IllegalArgumentException.class,
        () -> OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 1, 3, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> OTP.issue(ID, CUSTOMER, state(OtpStatus.USED, 1, 3, NOW)));
    assertThrows(
        IllegalArgumentException.class,
        () -> OTP.issue(ID, CUSTOMER, state(OtpStatus.LOCKED, 3, 3, null)));
    assertThrows(
        IllegalArgumentException.class,
        () -> OTP.issue(ID, CUSTOMER, state(OtpStatus.EXPIRED, 1, 3, null)));
  }

  @Test
  void issueRejectsNullArgumentsAtRuntime() {
    OtpSnapshot initial = state(OtpStatus.ACTIVE, 0, 3, null);
    assertThrows(NullPointerException.class, () -> OTP.issue(null, CUSTOMER, initial));
    assertThrows(NullPointerException.class, () -> OTP.issue(ID, null, initial));
    assertThrows(NullPointerException.class, () -> OTP.issue(ID, CUSTOMER, null));
  }

  @Test
  void restoreAcceptsHistoricalStatesWithoutReset() {
    for (OtpSnapshot state :
        new OtpSnapshot[] {
          state(OtpStatus.ACTIVE, 2, 3, null),
          state(OtpStatus.USED, 2, 3, NOW.plusSeconds(1)),
          state(OtpStatus.LOCKED, 3, 3, null),
          state(OtpStatus.EXPIRED, 1, 3, null),
          state(OtpStatus.INVALIDATED, 1, 3, null)
        }) {
      OTP restored = OTP.restore(ID, CUSTOMER, state);
      assertEquals(ID, restored.getId());
      assertEquals(CUSTOMER, restored.getCustomerId());
      assertSame(state, restored.getSnapshot());
      assertEquals(state.attempts(), restored.getSnapshot().attempts());
      assertEquals(state.consumedAt(), restored.getSnapshot().consumedAt());
    }
  }

  @Test
  void restoreRejectsNullArgumentsAtRuntime() {
    OtpSnapshot existing = state(OtpStatus.USED, 1, 3, NOW);
    assertThrows(NullPointerException.class, () -> OTP.restore(null, CUSTOMER, existing));
    assertThrows(NullPointerException.class, () -> OTP.restore(ID, null, existing));
    assertThrows(NullPointerException.class, () -> OTP.restore(ID, CUSTOMER, null));
  }

  @Test
  void correctCodeSucceedsOnLastRemainingAttemptAfterPreviousFailures() {
    OTP otp = OTP.restore(ID, CUSTOMER, state(OtpStatus.ACTIVE, 2, 3, null));
    assertEquals(OtpVerificationResult.VERIFIED, otp.verify(command("123456", NOW), HASHER));
    assertEquals(OtpStatus.USED, otp.getSnapshot().status());
    assertEquals(3, otp.getSnapshot().attempts());
    assertEquals(NOW, otp.getSnapshot().consumedAt());
  }

  @Test
  void failedAttemptDoesNotRequireDeviceBindingOrConsumptionTime() {
    OTP otp = OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null));
    OtpSnapshot before = otp.getSnapshot();
    assertEquals(OtpVerificationResult.INVALID_CODE, otp.verify(command("000000", NOW), HASHER));
    assertEquals(OtpStatus.ACTIVE, otp.getSnapshot().status());
    assertEquals(1, otp.getSnapshot().attempts());
    assertNull(otp.getSnapshot().consumedAt());
    assertNull(otp.getSnapshot().deviceBindingHash());
    assertEquals(0, before.attempts());
  }

  @Test
  void consumedChallengeCannotBeReusedOrMutated() {
    OTP otp = OTP.restore(ID, CUSTOMER, state(OtpStatus.USED, 1, 3, NOW));
    OtpSnapshot before = otp.getSnapshot();
    assertEquals(
        OtpVerificationResult.ALREADY_USED,
        otp.verify(command("123456", NOW.plusSeconds(2)), HASHER));
    assertSame(before, otp.getSnapshot());
  }

  @Test
  void lockedChallengeCannotBeReusedOrMutated() {
    OTP otp = OTP.restore(ID, CUSTOMER, state(OtpStatus.LOCKED, 3, 3, null));
    OtpSnapshot before = otp.getSnapshot();
    assertEquals(OtpVerificationResult.LOCKED, otp.verify(command("123456", NOW), HASHER));
    assertSame(before, otp.getSnapshot());
  }

  @Test
  void verificationAtExpirationTransitionsToExpiredWithoutCountingAttempt() {
    OTP otp = OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null));
    assertEquals(OtpVerificationResult.EXPIRED, otp.verify(command("123456", EXPIRES), HASHER));
    assertEquals(OtpStatus.EXPIRED, otp.getSnapshot().status());
    assertEquals(0, otp.getSnapshot().attempts());
    assertNull(otp.getSnapshot().consumedAt());
  }

  @Test
  void verificationBeforeCreationIsInvalidEvenForWrongCode() {
    OTP otp = OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null));
    OtpSnapshot before = otp.getSnapshot();
    assertThrows(
        IllegalArgumentException.class,
        () -> otp.verify(command("000000", NOW.minusSeconds(1)), HASHER));
    assertSame(before, otp.getSnapshot());
  }

  @Test
  void hasherFailureInDeviceBindingGuardPreservesState() {
    OtpSnapshot withBinding =
        new OtpSnapshot(
            OtpPurpose.LOGIN,
            OtpChannel.SMS,
            OtpVerificationMode.RANDOM_NUMERIC,
            "hash:123456",
            "hash:device-a",
            3,
            NOW,
            EXPIRES,
            RETAINS,
            0,
            OtpStatus.ACTIVE,
            null);
    OTP otp = OTP.issue(ID, CUSTOMER, withBinding);
    OtpCodeHasher failing =
        new OtpCodeHasher() {
          @Override
          public String hash(String value) {
            throw new IllegalStateException("hash unavailable");
          }

          @Override
          public boolean matches(String value, String encodedHash) {
            throw new IllegalStateException("hash unavailable");
          }
        };
    assertThrows(
        IllegalStateException.class,
        () ->
            otp.verify(
                new VerifyOtpCommand(OtpPurpose.LOGIN, "123456", "device-a", NOW, false), failing));
    assertSame(withBinding, otp.getSnapshot());
  }

  @Test
  void invalidatingActiveChallengeIsCompleteAndIdempotentInMemory() {
    OTP otp = OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null));
    otp.invalidate();
    assertEquals(OtpStatus.INVALIDATED, otp.getSnapshot().status());
    assertEquals(0, otp.getSnapshot().attempts());
    OtpSnapshot invalidated = otp.getSnapshot();
    otp.invalidate();
    assertSame(invalidated, otp.getSnapshot());
  }

  @Test
  void invalidatingUsedChallengeDoesNotChangeIt() {
    OTP otp = OTP.restore(ID, CUSTOMER, state(OtpStatus.USED, 1, 3, NOW));
    OtpSnapshot before = otp.getSnapshot();
    otp.invalidate();
    assertSame(before, otp.getSnapshot());
  }

  @Test
  void identityHashingDoesNotChangeWhenSnapshotChanges() {
    OTP otp = OTP.issue(ID, CUSTOMER, state(OtpStatus.ACTIVE, 0, 3, null));
    Set<OTP> known = new HashSet<>();
    known.add(otp);
    int beforeHash = otp.hashCode();
    assertEquals(OtpVerificationResult.VERIFIED, otp.verify(command("123456", NOW), HASHER));
    assertEquals(beforeHash, otp.hashCode());
    assertTrue(known.contains(otp));
    OTP restored = OTP.restore(ID, CUSTOMER, otp.getSnapshot());
    assertEquals(otp, restored);
    assertFalse(otp == restored);
  }

  private static VerifyOtpCommand command(String code, Instant now) {
    return new VerifyOtpCommand(OtpPurpose.LOGIN, code, null, now, false);
  }

  private static OtpSnapshot state(
      OtpStatus status, int attempts, int maxAttempts, Instant consumedAt) {
    return new OtpSnapshot(
        OtpPurpose.LOGIN,
        OtpChannel.SMS,
        OtpVerificationMode.RANDOM_NUMERIC,
        "hash:123456",
        null,
        maxAttempts,
        NOW,
        EXPIRES,
        RETAINS,
        attempts,
        status,
        consumedAt);
  }
}
