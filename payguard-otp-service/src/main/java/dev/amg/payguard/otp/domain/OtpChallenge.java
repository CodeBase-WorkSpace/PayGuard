package dev.amg.payguard.otp.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
public final class OtpChallenge {

  private static final int MIN_ATTEMPTS = 1;

  private final UUID id;
  private final String userId;
  private final OtpPurpose purpose;
  private final OtpChannel channel;
  private final OtpVerificationMode verificationMode;
  private final String codeHash;
  private final String deviceBindingHash;
  private final int maxAttempts;
  private final Instant createdAt;
  private final Instant expiresAt;
  private final Instant retentionUntil;
  private int attempts;
  private OtpChallengeStatus status;
  private Instant consumedAt;

  private OtpChallenge(
      UUID id,
      String userId,
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationMode verificationMode,
      String codeHash,
      String deviceBindingHash,
      int maxAttempts,
      Instant createdAt,
      Instant expiresAt,
      Instant retentionUntil,
      int attempts,
      OtpChallengeStatus status,
      Instant consumedAt) {
    this.id = Objects.requireNonNull(id);
    this.userId = requireText(userId, "userId");
    this.purpose = Objects.requireNonNull(purpose);
    this.channel = Objects.requireNonNull(channel);
    this.verificationMode = Objects.requireNonNull(verificationMode);
    this.codeHash = codeHash;
    this.deviceBindingHash = deviceBindingHash;
    if (maxAttempts < MIN_ATTEMPTS) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    this.maxAttempts = maxAttempts;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.retentionUntil = Objects.requireNonNull(retentionUntil);
    this.attempts = attempts;
    this.status = Objects.requireNonNull(status);
    this.consumedAt = consumedAt;
  }

  public static OtpChallenge issue(
      UUID id,
      String userId,
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationMode verificationMode,
      String codeHash,
      String deviceBindingHash,
      int maxAttempts,
      Instant createdAt,
      Instant expiresAt,
      Instant retentionUntil) {
    return new OtpChallenge(
        id,
        userId,
        purpose,
        channel,
        verificationMode,
        codeHash,
        deviceBindingHash,
        maxAttempts,
        createdAt,
        expiresAt,
        retentionUntil,
        0,
        OtpChallengeStatus.ACTIVE,
        null);
  }

  public static OtpChallenge restore(
      UUID id,
      String userId,
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationMode verificationMode,
      String codeHash,
      String deviceBindingHash,
      int maxAttempts,
      Instant createdAt,
      Instant expiresAt,
      Instant retentionUntil,
      int attempts,
      OtpChallengeStatus status,
      Instant consumedAt) {
    return new OtpChallenge(
        id,
        userId,
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

  public OtpVerificationResult verify(
      OtpPurpose requestedPurpose,
      String code,
      String deviceFingerprint,
      OtpCodeHasher hasher,
      boolean authenticatorCodeValid,
      Instant now) {
    if (requestedPurpose != purpose) {
      return OtpVerificationResult.PURPOSE_MISMATCH;
    }
    if (status == OtpChallengeStatus.USED) {
      return OtpVerificationResult.ALREADY_USED;
    }
    if (status == OtpChallengeStatus.LOCKED) {
      return OtpVerificationResult.LOCKED;
    }
    if (status == OtpChallengeStatus.INVALIDATED) {
      return OtpVerificationResult.INVALIDATED;
    }
    if (status == OtpChallengeStatus.EXPIRED || !now.isBefore(expiresAt)) {
      status = OtpChallengeStatus.EXPIRED;
      return OtpVerificationResult.EXPIRED;
    }
    if (deviceBindingHash != null
        && (deviceFingerprint == null || !hasher.matches(deviceFingerprint, deviceBindingHash))) {
      return OtpVerificationResult.DEVICE_MISMATCH;
    }

    attempts++;
    boolean valid =
        verificationMode == OtpVerificationMode.TOTP
            ? authenticatorCodeValid
            : code != null && codeHash != null && hasher.matches(code, codeHash);
    if (!valid) {
      if (attempts >= maxAttempts) {
        status = OtpChallengeStatus.LOCKED;
      }
      return status == OtpChallengeStatus.LOCKED
          ? OtpVerificationResult.LOCKED
          : OtpVerificationResult.INVALID_CODE;
    }
    status = OtpChallengeStatus.USED;
    consumedAt = now;
    return OtpVerificationResult.VERIFIED;
  }

  public void invalidate() {
    if (status == OtpChallengeStatus.ACTIVE) {
      status = OtpChallengeStatus.INVALIDATED;
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  public UUID id() {
    return id;
  }

  public String userId() {
    return userId;
  }

  public OtpPurpose purpose() {
    return purpose;
  }

  public OtpChannel channel() {
    return channel;
  }

  public OtpVerificationMode verificationMode() {
    return verificationMode;
  }

  public String codeHash() {
    return codeHash;
  }

  public String deviceBindingHash() {
    return deviceBindingHash;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public int attempts() {
    return attempts;
  }

  public OtpChallengeStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant retentionUntil() {
    return retentionUntil;
  }

  public Instant consumedAt() {
    return consumedAt;
  }
}
