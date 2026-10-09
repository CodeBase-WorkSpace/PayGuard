package dev.amg.payguard.otp.domain.model;

import dev.amg.payguard.otp.domain.enums.OtpChannel;
import dev.amg.payguard.otp.domain.enums.OtpPurpose;
import dev.amg.payguard.otp.domain.enums.OtpStatus;
import dev.amg.payguard.otp.domain.enums.OtpVerificationMode;
import dev.amg.payguard.otp.domain.exception.InvalidOtpAttemptLimitException;
import dev.amg.payguard.otp.domain.exception.OtpAttemptCountOutOfRangeException;
import dev.amg.payguard.otp.domain.exception.OtpCodeHashMustNotBeBlankException;
import dev.amg.payguard.otp.domain.exception.OtpCodeHashRequiredForRandomNumericVerificationException;
import dev.amg.payguard.otp.domain.exception.OtpConsumedTimeMustMatchUsedStatusException;
import dev.amg.payguard.otp.domain.exception.OtpConsumedTimeOutsideValidityPeriodException;
import dev.amg.payguard.otp.domain.exception.OtpDeviceBindingHashMustNotBeBlankException;
import dev.amg.payguard.otp.domain.exception.OtpExpirationTimeMustBeAfterCreationTimeException;
import dev.amg.payguard.otp.domain.policy.OtpRetentionPolicy;

import java.time.Instant;
import java.util.Objects;

public record OtpSnapshot(
        OtpPurpose purpose, OtpChannel channel,
        OtpVerificationMode verificationMode, String codeHash,
        String deviceBindingHash, int maxAttempts,
        Instant createdAt, Instant expiresAt,
        Instant retentionUntil, int attempts,
        OtpStatus status, Instant consumedAt
) {
   private static final int MIN_ATTEMPTS = 1;

   public OtpSnapshot {
      validateRequiredFields(purpose, channel, verificationMode, createdAt, expiresAt, retentionUntil, status);
      validateAttempts(status, attempts, maxAttempts);
      validateVerificationData(verificationMode, codeHash, deviceBindingHash);
      validateLifecycle(status, createdAt, expiresAt, consumedAt);
      OtpRetentionPolicy.validate(expiresAt, retentionUntil);
   }

   public static OtpSnapshot reconstitute(
           OtpPurpose purpose, OtpChannel channel,
           OtpVerificationMode verificationMode, String codeHash,
           String deviceBindingHash, int maxAttempts,
           Instant createdAt, Instant expiresAt, Instant retentionUntil,
           int attempts, OtpStatus status, Instant consumedAt) {
      return new OtpSnapshot(purpose, channel, verificationMode, codeHash,
              deviceBindingHash, maxAttempts, createdAt, expiresAt, retentionUntil,
              attempts, status, consumedAt);
   }


   private static void validateRequiredFields(OtpPurpose purpose, OtpChannel channel,
                                              OtpVerificationMode verificationMode, Instant createdAt,
                                              Instant expiresAt, Instant retentionUntil, OtpStatus status) {

      Objects.requireNonNull(purpose, "purpose must not be null");
      Objects.requireNonNull(channel, "channel must not be null");
      Objects.requireNonNull(verificationMode, "verificationMode must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(expiresAt, "expiresAt must not be null");
      Objects.requireNonNull(retentionUntil, "retentionUntil must not be null");
      Objects.requireNonNull(status, "status must not be null");
   }

   OtpSnapshot withStatus(OtpStatus newStatus) {
      return toBuilder().status(newStatus).build();
   }

   OtpSnapshot withAttemptRecorded() {
      return toBuilder().attempts(attempts + 1).build();
   }

   OtpSnapshot withFailedAttemptAndLock() {
      return toBuilder()
              .attempts(attempts + 1)
              .status(OtpStatus.LOCKED)
              .build();
   }
   OtpSnapshot withSuccessfulAttempt(Instant verifiedAt) {
      return toBuilder()
              .attempts(attempts + 1)
              .status(OtpStatus.USED)
              .consumedAt(verifiedAt)
              .build();
   }

   private static void validateAttempts(OtpStatus status, int attempts, int maxAttempts) {
      if (maxAttempts < MIN_ATTEMPTS) {
         throw new InvalidOtpAttemptLimitException(maxAttempts, MIN_ATTEMPTS);
      }

      if (attempts < 0 || attempts > maxAttempts) {
         throw new OtpAttemptCountOutOfRangeException(attempts, maxAttempts);
      }

      if (status == OtpStatus.ACTIVE && attempts == maxAttempts) {
         throw new OtpAttemptCountOutOfRangeException(attempts, maxAttempts);
      }
   }

   private static void validateVerificationData(OtpVerificationMode verificationMode, String codeHash, String deviceBindingHash) {
      if (verificationMode == OtpVerificationMode.RANDOM_NUMERIC
              && (codeHash == null || codeHash.isBlank())) {
         throw new OtpCodeHashRequiredForRandomNumericVerificationException(verificationMode);

      }

      if (Objects.nonNull(codeHash) && codeHash.isBlank()) {
         throw new OtpCodeHashMustNotBeBlankException();
      }

      if (Objects.nonNull(deviceBindingHash) && deviceBindingHash.isBlank()) {
         throw new OtpDeviceBindingHashMustNotBeBlankException();
      }

   }

   private static void validateLifecycle(OtpStatus status, Instant createdAt, Instant expiresAt, Instant consumedAt) {
      if (!expiresAt.isAfter(createdAt)) {
         throw new OtpExpirationTimeMustBeAfterCreationTimeException();
      }

      final boolean isUsed = status == OtpStatus.USED;
      final boolean hasConsumedAt = Objects.nonNull(consumedAt);

      if (isUsed != hasConsumedAt) {
         throw new OtpConsumedTimeMustMatchUsedStatusException(consumedAt, createdAt, expiresAt);
      }

      if (hasConsumedAt
              && (consumedAt.isBefore(createdAt)
              || !consumedAt.isBefore(expiresAt))) {
         throw new OtpConsumedTimeOutsideValidityPeriodException(consumedAt, createdAt, expiresAt);
      }
   }

   Builder toBuilder() {
      return new Builder()
              .purpose(purpose)
              .channel(channel)
              .verificationMode(verificationMode)
              .codeHash(codeHash)
              .deviceBindingHash(deviceBindingHash)
              .maxAttempts(maxAttempts)
              .createdAt(createdAt)
              .expiresAt(expiresAt)
              .retentionUntil(retentionUntil)
              .attempts(attempts)
              .status(status)
              .consumedAt(consumedAt);
   }

   static final class Builder {

      private OtpPurpose purpose;
      private OtpChannel channel;
      private OtpVerificationMode verificationMode;
      private String codeHash;
      private String deviceBindingHash;
      private int maxAttempts;
      private Instant createdAt;
      private Instant expiresAt;
      private Instant retentionUntil;
      private int attempts;
      private OtpStatus status;
      private Instant consumedAt;

      private Builder() {
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

      Builder codeHash(String codeHash) {
         this.codeHash = codeHash;
         return this;
      }

      Builder deviceBindingHash(String hash) {
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

      Builder consumedAt(Instant consumedAt) {
         this.consumedAt = consumedAt;
         return this;
      }

      OtpSnapshot build() {
         return new OtpSnapshot(purpose, channel, verificationMode, codeHash,
                 deviceBindingHash, maxAttempts, createdAt, expiresAt, retentionUntil,
                 attempts, status, consumedAt);
      }
   }

}
