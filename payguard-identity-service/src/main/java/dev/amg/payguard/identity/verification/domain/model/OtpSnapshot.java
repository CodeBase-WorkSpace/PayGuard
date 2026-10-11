package dev.amg.payguard.identity.verification.domain.model;

import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.exception.InvalidOtpAttemptLimitException;
import dev.amg.payguard.identity.verification.domain.exception.OtpAttemptCountOutOfRangeException;
import dev.amg.payguard.identity.verification.domain.exception.OtpCodeHashMustNotBeBlankException;
import dev.amg.payguard.identity.verification.domain.exception.OtpCodeHashRequiredForRandomNumericVerificationException;
import dev.amg.payguard.identity.verification.domain.exception.OtpConsumedTimeMustMatchUsedStatusException;
import dev.amg.payguard.identity.verification.domain.exception.OtpConsumedTimeOutsideValidityPeriodException;
import dev.amg.payguard.identity.verification.domain.exception.OtpDeviceBindingHashMustNotBeBlankException;
import dev.amg.payguard.identity.verification.domain.exception.OtpExpirationTimeMustBeAfterCreationTimeException;
import dev.amg.payguard.identity.verification.domain.policy.OtpRetentionPolicy;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An immutable snapshot of an OTP challenge's state.
 *
 * <p>Contains the verification settings, attempt count, status, and lifecycle timestamps. Changes
 * produce a new validated snapshot without modifying the existing one.
 *
 * <h2>Attempt limits</h2>
 *
 * <p>The maximum number of attempts must be positive. The recorded attempt count must be between
 * zero and that maximum, inclusive. An ACTIVE snapshot must have at least one attempt remaining.
 *
 * <p>Both successful and failed code checks count as attempts. Recording the last failed attempt
 * and setting the status to LOCKED must happen in the same new snapshot to avoid an invalid
 * intermediate state.
 *
 * <h2>Verification data</h2>
 *
 * <p>RANDOM_NUMERIC verification requires a nonblank code hash. The device-binding hash is
 * optional. Neither hash may be blank when present.
 *
 * <h2>Validity and consumption</h2>
 *
 * <p>Expiration must be strictly after creation. A consumption time must be present exactly when
 * the status is USED.
 *
 * <p>Consumption is allowed at creation time, but not at or after expiration: {@code createdAt <=
 * consumedAt < expiresAt}.
 *
 * <p>Retention constraints are checked by {@link OtpRetentionPolicy}. Retention does not extend the
 * challenge's verification validity.
 *
 * <h2>Responsibility</h2>
 *
 * <p>This record validates the resulting state. The OTP aggregate decides whether a requested state
 * transition is allowed. Constructing a valid snapshot does not prove that an OTP code was
 * verified.
 *
 * @implSpec Every constructor call validates the supplied state. The factory and builder use the
 *     same constructor validation. All components are immutable.
 */
public record OtpSnapshot(
    OtpPurpose purpose,
    OtpChannel channel,
    OtpVerificationMode verificationMode,
    @Nullable String codeHash,
    @Nullable String deviceBindingHash,
    int maxAttempts,
    Instant createdAt,
    Instant expiresAt,
    Instant retentionUntil,
    int attempts,
    OtpStatus status,
    @Nullable Instant consumedAt) {
  private static final int MIN_ATTEMPTS = 1;

  public OtpSnapshot {
    validateRequiredFields(
        purpose, channel, verificationMode, createdAt, expiresAt, retentionUntil, status);
    validateAttempts(status, attempts, maxAttempts);
    validateVerificationData(verificationMode, codeHash, deviceBindingHash);
    validateLifecycle(status, createdAt, expiresAt, consumedAt);
    OtpRetentionPolicy.validate(expiresAt, retentionUntil);
  }

  /**
   * Creates a validated snapshot. The supplied state does not have to represent a newly issued OTP.
   */
  public static OtpSnapshot create(
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationMode verificationMode,
      @Nullable String codeHash,
      @Nullable String deviceBindingHash,
      int maxAttempts,
      Instant createdAt,
      Instant expiresAt,
      Instant retentionUntil,
      int attempts,
      OtpStatus status,
      @Nullable Instant consumedAt) {
    return new OtpSnapshot(
        purpose,
        channel,
        verificationMode,
        codeHash,
        deviceBindingHash,
        maxAttempts,
        createdAt,
        expiresAt,
        retentionUntil,
        attempts,
        status,
        consumedAt);
  }

  private static void validateRequiredFields(
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationMode verificationMode,
      Instant createdAt,
      Instant expiresAt,
      Instant retentionUntil,
      OtpStatus status) {

    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(channel, "channel must not be null");
    Objects.requireNonNull(verificationMode, "verificationMode must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(retentionUntil, "retentionUntil must not be null");
    Objects.requireNonNull(status, "status must not be null");
  }

  private static void validateAttempts(OtpStatus status, int attempts, int maxAttempts) {
    if (maxAttempts < MIN_ATTEMPTS) {
      throw new InvalidOtpAttemptLimitException(maxAttempts, MIN_ATTEMPTS);
    }

    if (attempts < 0 || attempts > maxAttempts) {
      throw new OtpAttemptCountOutOfRangeException(attempts, maxAttempts);
    }
    /* An ACTIVE OTP must have at least one attempt remaining. */
    if (status == OtpStatus.ACTIVE && attempts == maxAttempts) {
      throw new OtpAttemptCountOutOfRangeException(attempts, maxAttempts);
    }
  }

  private static void validateVerificationData(
      OtpVerificationMode verificationMode,
      @Nullable String codeHash,
      @Nullable String deviceBindingHash) {
    if (verificationMode == OtpVerificationMode.RANDOM_NUMERIC
        && (codeHash == null || codeHash.isBlank())) {
      throw new OtpCodeHashRequiredForRandomNumericVerificationException(verificationMode);
    }

    if (codeHash != null && codeHash.isBlank()) {
      throw new OtpCodeHashMustNotBeBlankException();
    }

    /* Null means no device binding; a supplied hash must not be blank. */
    if (deviceBindingHash != null && deviceBindingHash.isBlank()) {
      throw new OtpDeviceBindingHashMustNotBeBlankException();
    }
  }

  private static void validateLifecycle(
      OtpStatus status, Instant createdAt, Instant expiresAt, @Nullable Instant consumedAt) {
    if (!expiresAt.isAfter(createdAt)) {
      throw new OtpExpirationTimeMustBeAfterCreationTimeException();
    }

    final boolean isUsed = status == OtpStatus.USED;
    final boolean hasConsumedAt = consumedAt != null;

    if (isUsed != hasConsumedAt) {
      throw new OtpConsumedTimeMustMatchUsedStatusException(consumedAt, createdAt, expiresAt);
    }
    if (consumedAt != null && (consumedAt.isBefore(createdAt) || !consumedAt.isBefore(expiresAt))) {
      throw new OtpConsumedTimeOutsideValidityPeriodException(consumedAt, createdAt, expiresAt);
    }
  }

  /* Fluent builder methods deliberately match their state fields. */
  @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
  static final class Builder {

    private OtpPurpose purpose;
    private OtpChannel channel;
    private OtpVerificationMode verificationMode;
    @Nullable private String codeHash;
    @Nullable private String deviceBindingHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant retentionUntil;
    private OtpStatus status;
    @Nullable private Instant consumedAt;
    private int maxAttempts;
    private int attempts;

    private Builder(OtpSnapshot snapshot) {
      this.purpose = snapshot.purpose();
      this.channel = snapshot.channel();
      this.verificationMode = snapshot.verificationMode();
      this.codeHash = snapshot.codeHash();
      this.deviceBindingHash = snapshot.deviceBindingHash();
      this.createdAt = snapshot.createdAt();
      this.expiresAt = snapshot.expiresAt();
      this.retentionUntil = snapshot.retentionUntil();
      this.status = snapshot.status();
      this.consumedAt = snapshot.consumedAt();
      this.maxAttempts = snapshot.maxAttempts();
      this.attempts = snapshot.attempts();
    }

    Builder purpose(OtpPurpose purpose) {
      this.purpose = purpose;
      return this;
    }

    Builder channel(OtpChannel channel) {
      this.channel = channel;
      return this;
    }

    Builder verificationMode(OtpVerificationMode mode) {
      this.verificationMode = mode;
      return this;
    }

    Builder codeHash(@Nullable String codeHash) {
      this.codeHash = codeHash;
      return this;
    }

    Builder deviceBindingHash(@Nullable String hash) {
      this.deviceBindingHash = hash;
      return this;
    }

    Builder maxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
      return this;
    }

    Builder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    Builder retentionUntil(Instant retentionUntil) {
      this.retentionUntil = retentionUntil;
      return this;
    }

    Builder attempts(int attempts) {
      this.attempts = attempts;
      return this;
    }

    Builder status(OtpStatus status) {
      this.status = status;
      return this;
    }

    Builder consumedAt(@Nullable Instant consumedAt) {
      this.consumedAt = consumedAt;
      return this;
    }

    OtpSnapshot build() {
      return new OtpSnapshot(
          purpose,
          channel,
          verificationMode,
          codeHash,
          deviceBindingHash,
          maxAttempts,
          createdAt,
          expiresAt,
          retentionUntil,
          attempts,
          status,
          consumedAt);
    }
  }

  OtpSnapshot withStatus(OtpStatus newStatus) {
    return toBuilder().status(newStatus).build();
  }

  /**
   * Records one attempt without changing the status. Use withFailedAttemptAndLock for the last
   * failed attempt.
   */
  OtpSnapshot withAttemptRecorded() {
    return toBuilder().attempts(attempts + 1).build();
  }

  /**
   * Records a failed attempt and locks the OTP in the same snapshot. This avoids creating an ACTIVE
   * snapshot with no attempts remaining.
   */
  OtpSnapshot withFailedAttemptAndLock() {
    return toBuilder().attempts(attempts + 1).status(OtpStatus.LOCKED).build();
  }

  /**
   * Records a successful attempt, marks the OTP as USED, and sets its consumption time in the same
   * snapshot. Successful attempts also count toward the attempt limit.
   */
  OtpSnapshot withSuccessfulAttempt(Instant verifiedAt) {
    return toBuilder().attempts(attempts + 1).status(OtpStatus.USED).consumedAt(verifiedAt).build();
  }

  @Override
  public String toString() {
    return "OtpSnapshot[purpose="
        + purpose
        + ", status="
        + status
        + ", attempts="
        + attempts
        + ", codeHash=[REDACTED], deviceBindingHash=[REDACTED]]";
  }

  Builder toBuilder() {
    return new Builder(this);
  }
}
