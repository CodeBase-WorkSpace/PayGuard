package dev.amg.payguard.identity.verification.domain;

import static org.junit.jupiter.api.Assertions.*;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.*;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OTPTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(300);
  private static final Instant RETENTION_UNTIL =
      EXPIRES_AT.atZone(ZoneOffset.UTC).plusYears(3).toInstant();

  private static final OtpId ID = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
  private static final CustomerId CUSTOMER_ID = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");

  // Test double only. Never use this hasher in production.
  private static final OtpCodeHasher HASHER =
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

  private static OtpSnapshot snapshot(
      OtpStatus status,
      int attempts,
      int maxAttempts,
      @Nullable Instant consumedAt,
      @Nullable String deviceHash) {

    return new OtpSnapshot(
        OtpPurpose.LOGIN,
        OtpChannel.SMS,
        OtpVerificationMode.RANDOM_NUMERIC,
        "hash:123456",
        deviceHash,
        maxAttempts,
        CREATED_AT,
        EXPIRES_AT,
        RETENTION_UNTIL,
        attempts,
        status,
        consumedAt);
  }

  private static OTP issue(int maxAttempts) {
    return OTP.issue(ID, CUSTOMER_ID, snapshot(OtpStatus.ACTIVE, 0, maxAttempts, null, null));
  }

  private static VerifyOtpCommand command(
      OtpPurpose purpose, String code, Instant now, @Nullable String device) {

    return new VerifyOtpCommand(purpose, code, device, now, false);
  }

  @Test
  void issueRequiresFreshActiveSnapshot() {
    OtpSnapshot attempted = snapshot(OtpStatus.ACTIVE, 1, 3, null, null);

    OtpSnapshot consumed = snapshot(OtpStatus.USED, 1, 3, CREATED_AT, null);

    assertThrows(IllegalArgumentException.class, () -> OTP.issue(ID, CUSTOMER_ID, attempted));

    assertThrows(IllegalArgumentException.class, () -> OTP.issue(ID, CUSTOMER_ID, consumed));
  }

  @Test
  void restorePreservesValidHistoricalStates() {
    OtpSnapshot[] historicalStates = {
      snapshot(OtpStatus.ACTIVE, 2, 3, null, null),
      snapshot(OtpStatus.USED, 2, 3, CREATED_AT.plusSeconds(20), null),
      snapshot(OtpStatus.LOCKED, 3, 3, null, null),
      snapshot(OtpStatus.EXPIRED, 1, 3, null, null),
      snapshot(OtpStatus.INVALIDATED, 0, 3, null, null)
    };

    for (OtpSnapshot state : historicalStates) {
      OTP restored = OTP.restore(ID, CUSTOMER_ID, state);

      assertEquals(ID, restored.getId());
      assertEquals(CUSTOMER_ID, restored.getCustomerId());
      assertEquals(state, restored.getSnapshot(), state.status().name());
    }
  }

  @Test
  void restoreRejectsNullArguments() {
    OtpSnapshot valid = snapshot(OtpStatus.ACTIVE, 0, 3, null, null);

    // Deliberate violations of the non-null contract.
    assertThrows(NullPointerException.class, () -> OTP.restore(null, CUSTOMER_ID, valid));

    assertThrows(NullPointerException.class, () -> OTP.restore(ID, null, valid));

    assertThrows(NullPointerException.class, () -> OTP.restore(ID, CUSTOMER_ID, null));
  }

  @Test
  void restoredUsedOtpCannotBeVerifiedAgain() {
    OtpSnapshot used = snapshot(OtpStatus.USED, 2, 3, CREATED_AT.plusSeconds(20), null);

    OTP otp = OTP.restore(ID, CUSTOMER_ID, used);
    OtpSnapshot before = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.ALREADY_USED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT.plusSeconds(30), null), HASHER));

    assertSame(before, otp.getSnapshot());
  }

  @Test
  void invalidCodeCountsOneAttemptWithoutMutatingPreviousSnapshot() {
    OTP otp = issue(3);
    OtpSnapshot before = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        otp.verify(command(OtpPurpose.LOGIN, "000000", CREATED_AT, null), HASHER));

    assertEquals(OtpStatus.ACTIVE, otp.getSnapshot().status());
    assertEquals(1, otp.getSnapshot().attempts());
    assertNull(otp.getSnapshot().consumedAt());

    assertEquals(0, before.attempts());
    assertEquals(OtpStatus.ACTIVE, before.status());
    assertNotSame(before, otp.getSnapshot());
  }

  @Test
  void finalInvalidAttemptLocksOtpAndPreventsFurtherVerification() {
    OTP otp = issue(2);

    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        otp.verify(command(OtpPurpose.LOGIN, "000000", CREATED_AT, null), HASHER));

    assertEquals(
        OtpVerificationResult.LOCKED,
        otp.verify(command(OtpPurpose.LOGIN, "000000", CREATED_AT, null), HASHER));

    assertEquals(OtpStatus.LOCKED, otp.getSnapshot().status());
    assertEquals(2, otp.getSnapshot().attempts());
    assertNull(otp.getSnapshot().consumedAt());

    OtpSnapshot locked = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.LOCKED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, null), HASHER));

    assertSame(locked, otp.getSnapshot());
  }

  @Test
  void successOnLastAvailableAttemptIsSingleUse() {
    OTP otp = issue(2);

    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        otp.verify(command(OtpPurpose.LOGIN, "000000", CREATED_AT, null), HASHER));

    Instant verifiedAt = CREATED_AT.plusSeconds(10);

    assertEquals(
        OtpVerificationResult.VERIFIED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", verifiedAt, null), HASHER));

    assertEquals(OtpStatus.USED, otp.getSnapshot().status());
    assertEquals(2, otp.getSnapshot().attempts());
    assertEquals(verifiedAt, otp.getSnapshot().consumedAt());

    OtpSnapshot consumed = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.ALREADY_USED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", verifiedAt, null), HASHER));

    assertSame(consumed, otp.getSnapshot());
  }

  @Test
  void verificationAtExpirationMarksOtpExpiredWithoutCountingAttempt() {
    OTP otp = issue(3);

    assertEquals(
        OtpVerificationResult.EXPIRED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", EXPIRES_AT, null), HASHER));

    assertEquals(OtpStatus.EXPIRED, otp.getSnapshot().status());
    assertEquals(0, otp.getSnapshot().attempts());
    assertNull(otp.getSnapshot().consumedAt());

    OtpSnapshot expired = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.EXPIRED,
        otp.verify(command(OtpPurpose.LOGIN, "000000", EXPIRES_AT, null), HASHER));

    assertSame(expired, otp.getSnapshot());
  }

  @Test
  void purposeMismatchRejectsBeforeExpirationWithoutChangingState() {
    OTP otp = issue(3);
    OtpSnapshot before = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.PURPOSE_MISMATCH,
        otp.verify(command(OtpPurpose.LOAN_DISBURSE, "123456", EXPIRES_AT, null), HASHER));

    assertSame(before, otp.getSnapshot());
    assertEquals(0, otp.getSnapshot().attempts());
  }

  @Test
  void deviceMismatchDoesNotConsumeAttempt() {
    OTP otp = OTP.issue(ID, CUSTOMER_ID, snapshot(OtpStatus.ACTIVE, 0, 3, null, "hash:device-a"));

    OtpSnapshot before = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.DEVICE_MISMATCH,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, "device-b"), HASHER));

    assertSame(before, otp.getSnapshot());

    assertEquals(
        OtpVerificationResult.VERIFIED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, "device-a"), HASHER));

    assertEquals(OtpStatus.USED, otp.getSnapshot().status());
    assertEquals(1, otp.getSnapshot().attempts());
  }

  @Test
  void invalidationPreventsVerificationAndDoesNotChangeTerminalStates() {
    OTP otp = issue(3);

    otp.invalidate();

    assertEquals(OtpStatus.INVALIDATED, otp.getSnapshot().status());
    assertEquals(0, otp.getSnapshot().attempts());

    OtpSnapshot invalidated = otp.getSnapshot();

    assertEquals(
        OtpVerificationResult.INVALIDATED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, null), HASHER));

    otp.invalidate();
    assertSame(invalidated, otp.getSnapshot());

    OTP used = issue(1);
    used.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, null), HASHER);

    OtpSnapshot usedState = used.getSnapshot();
    used.invalidate();

    assertSame(usedState, used.getSnapshot());
    assertEquals(OtpStatus.USED, used.getSnapshot().status());
  }

  @Test
  void hasherExceptionLeavesExactSnapshotUnchanged() {
    OTP otp = issue(3);
    OtpSnapshot before = otp.getSnapshot();

    OtpCodeHasher failingHasher =
        new OtpCodeHasher() {
          @Override
          public String hash(String value) {
            throw new IllegalStateException("Hashing failed");
          }

          @Override
          public boolean matches(String value, String encodedHash) {
            throw new IllegalStateException("Hashing failed");
          }
        };

    assertThrows(
        IllegalStateException.class,
        () -> otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, null), failingHasher));

    assertSame(before, otp.getSnapshot());
  }

  @Test
  void verificationBeforeCreationIsRejectedWithoutMutation() {
    OTP otp = issue(3);
    OtpSnapshot before = otp.getSnapshot();

    // Proposed contract: invalid pre-creation time throws IllegalArgumentException.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            otp.verify(
                command(OtpPurpose.LOGIN, "123456", CREATED_AT.minusSeconds(1), null), HASHER));

    assertSame(before, otp.getSnapshot());
  }

  @Test
  void aggregateHashCodeRemainsStableAfterStateTransition() {
    OTP otp = issue(3);

    int hashBefore = otp.hashCode();
    Set<OTP> set = new HashSet<>();
    set.add(otp);

    assertEquals(
        OtpVerificationResult.VERIFIED,
        otp.verify(command(OtpPurpose.LOGIN, "123456", CREATED_AT, null), HASHER));

    assertEquals(hashBefore, otp.hashCode());
    assertTrue(set.contains(otp));
  }
}
