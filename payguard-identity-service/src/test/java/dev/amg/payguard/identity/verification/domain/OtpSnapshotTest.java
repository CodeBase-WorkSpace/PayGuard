package dev.amg.payguard.identity.verification.domain;

import static org.junit.jupiter.api.Assertions.*;

import dev.amg.payguard.identity.verification.domain.enums.*;
import dev.amg.payguard.identity.verification.domain.exception.*;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import java.time.Instant;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class OtpSnapshotTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(300);
  private static final Instant RETENTION_UNTIL =
      EXPIRES_AT.atZone(ZoneOffset.UTC).plusYears(3).toInstant();

  private static OtpSnapshot snapshot(
      OtpStatus status,
      int attempts,
      int maxAttempts,
      @Nullable Instant consumedAt,
      String codeHash,
      @Nullable String deviceHash,
      Instant expiresAt,
      Instant retentionUntil) {

    return new OtpSnapshot(
        OtpPurpose.LOGIN,
        OtpChannel.SMS,
        OtpVerificationMode.RANDOM_NUMERIC,
        codeHash,
        deviceHash,
        maxAttempts,
        CREATED_AT,
        expiresAt,
        retentionUntil,
        attempts,
        status,
        consumedAt);
  }

  private static OtpSnapshot state(
      OtpStatus status, int attempts, int maxAttempts, @Nullable Instant consumedAt) {

    return snapshot(
        status,
        attempts,
        maxAttempts,
        consumedAt,
        "hash:123456",
        null,
        EXPIRES_AT,
        RETENTION_UNTIL);
  }

  @Test
  void acceptsValidLifecycleStates() {
    OtpSnapshot active = state(OtpStatus.ACTIVE, 0, 3, null);
    OtpSnapshot used = state(OtpStatus.USED, 1, 3, CREATED_AT);
    OtpSnapshot locked = state(OtpStatus.LOCKED, 3, 3, null);
    OtpSnapshot expired = state(OtpStatus.EXPIRED, 1, 3, null);
    OtpSnapshot invalidated = state(OtpStatus.INVALIDATED, 0, 3, null);

    assertEquals(OtpStatus.ACTIVE, active.status());
    assertNull(active.consumedAt());
    assertNull(active.deviceBindingHash());

    assertEquals(OtpStatus.USED, used.status());
    assertEquals(CREATED_AT, used.consumedAt());

    assertEquals(OtpStatus.LOCKED, locked.status());
    assertEquals(OtpStatus.EXPIRED, expired.status());
    assertEquals(OtpStatus.INVALIDATED, invalidated.status());
  }

  @Test
  void rejectsInvalidAttemptLimits() {
    assertThrows(InvalidOtpAttemptLimitException.class, () -> state(OtpStatus.ACTIVE, 0, 0, null));

    assertThrows(
        OtpAttemptCountOutOfRangeException.class, () -> state(OtpStatus.ACTIVE, -1, 3, null));

    assertThrows(
        OtpAttemptCountOutOfRangeException.class, () -> state(OtpStatus.ACTIVE, 4, 3, null));

    // ACTIVE must always have an attempt remaining.
    assertThrows(
        OtpAttemptCountOutOfRangeException.class, () -> state(OtpStatus.ACTIVE, 3, 3, null));
  }

  @Test
  void consumedAtMustExistExactlyWhenStatusIsUsed() {
    assertThrows(
        OtpConsumedTimeMustMatchUsedStatusException.class, () -> state(OtpStatus.USED, 1, 3, null));

    assertThrows(
        OtpConsumedTimeMustMatchUsedStatusException.class,
        () -> state(OtpStatus.ACTIVE, 0, 3, CREATED_AT));

    assertThrows(
        OtpConsumedTimeMustMatchUsedStatusException.class,
        () -> state(OtpStatus.LOCKED, 3, 3, CREATED_AT));
  }

  @Test
  void consumedAtMustBeInsideValidityPeriod() {
    assertThrows(
        OtpConsumedTimeOutsideValidityPeriodException.class,
        () -> state(OtpStatus.USED, 1, 3, CREATED_AT.minusNanos(1)));

    assertThrows(
        OtpConsumedTimeOutsideValidityPeriodException.class,
        () -> state(OtpStatus.USED, 1, 3, EXPIRES_AT));

    // Consumption exactly at creation is valid.
    OtpSnapshot valid = state(OtpStatus.USED, 1, 3, CREATED_AT);
    assertEquals(CREATED_AT, valid.consumedAt());
  }

  @Test
  void expirationMustBeStrictlyAfterCreation() {
    assertThrows(
        OtpExpirationTimeMustBeAfterCreationTimeException.class,
        () ->
            snapshot(
                OtpStatus.ACTIVE, 0, 3, null, "hash:123456", null, CREATED_AT, RETENTION_UNTIL));

    assertThrows(
        OtpExpirationTimeMustBeAfterCreationTimeException.class,
        () ->
            snapshot(
                OtpStatus.ACTIVE,
                0,
                3,
                null,
                "hash:123456",
                null,
                CREATED_AT.minusSeconds(1),
                RETENTION_UNTIL));
  }

  @Test
  void retentionMustRespectMinimumPeriod() {
    // The current retention policy requires 3 years after expiry.
    OtpSnapshot valid = state(OtpStatus.ACTIVE, 0, 3, null);
    assertEquals(RETENTION_UNTIL, valid.retentionUntil());

    assertThrows(
        OtpRetentionPeriodMustNotPrecedeExpirationException.class,
        () ->
            snapshot(
                OtpStatus.ACTIVE,
                0,
                3,
                null,
                "hash:123456",
                null,
                EXPIRES_AT,
                RETENTION_UNTIL.minusNanos(1)));
  }

  @Test
  void numericCodeHashAndOptionalDeviceBindingAreValidated() {
    assertThrows(
        OtpCodeHashRequiredForRandomNumericVerificationException.class,
        () -> snapshot(OtpStatus.ACTIVE, 0, 3, null, "", null, EXPIRES_AT, RETENTION_UNTIL));

    assertThrows(
        OtpCodeHashRequiredForRandomNumericVerificationException.class,
        () -> snapshot(OtpStatus.ACTIVE, 0, 3, null, "   ", null, EXPIRES_AT, RETENTION_UNTIL));

    assertThrows(
        OtpDeviceBindingHashMustNotBeBlankException.class,
        () ->
            snapshot(
                OtpStatus.ACTIVE, 0, 3, null, "hash:123456", "  ", EXPIRES_AT, RETENTION_UNTIL));

    // No device binding is a valid configuration.
    OtpSnapshot unbound = state(OtpStatus.ACTIVE, 0, 3, null);
    assertNull(unbound.deviceBindingHash());
  }

  @Test
  void diagnosticStringMustNotExposeSensitiveHashes() {
    OtpSnapshot state =
        snapshot(
            OtpStatus.ACTIVE,
            0,
            3,
            null,
            "hash:123456",
            "hash:device-a",
            EXPIRES_AT,
            RETENTION_UNTIL);

    String diagnostic = state.toString();

    assertFalse(diagnostic.contains("hash:123456"));
    assertFalse(diagnostic.contains("hash:device-a"));
  }
}
