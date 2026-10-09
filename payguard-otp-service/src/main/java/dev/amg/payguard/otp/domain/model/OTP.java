package dev.amg.payguard.otp.domain.model;

import dev.amg.payguard.otp.domain.enums.OtpStatus;
import dev.amg.payguard.otp.domain.enums.OtpVerificationMode;
import dev.amg.payguard.otp.domain.enums.OtpVerificationResult;
import dev.amg.payguard.otp.domain.policy.OtpVerificationPolicy;
import dev.amg.payguard.otp.domain.port.CodeHasher;
import dev.amg.payguard.otp.domain.port.VerifyOTPCommand;
import domain.AggregateRoot;
import domain.DomainEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OTP extends AggregateRoot<UUID, DomainEvent> {

   private final String userId;
   private OtpSnapshot snapshot;

   private OTP(UUID id, String userId, OtpSnapshot snapshot) {
      super(Objects.requireNonNull(id, "id must not be null"));
      this.userId = Objects.requireNonNull(userId, "userId must not be null");
      this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
   }

   public static OTP issue(UUID id, String userId, OtpSnapshot snapshot) {
      Objects.requireNonNull(snapshot, "snapshot must not be null");

      if (snapshot.status() != OtpStatus.ACTIVE
              || snapshot.attempts() != 0
              || Objects.nonNull(snapshot.consumedAt())) {
         throw new IllegalArgumentException(
                 "issuance requires an initial snapshot");
      }

      return new OTP(id, userId, snapshot);
   }


   public static OTP restore(UUID id, String userId, OtpSnapshot snapshot) {
      return new OTP(id, userId, snapshot);
   }


   public OtpVerificationResult verify(VerifyOTPCommand command, CodeHasher hasher) {
      Optional<OtpVerificationResult> rejection =
              OtpVerificationPolicy.findRejection(command, snapshot, hasher);

      if (rejection.isPresent()) {
         OtpVerificationResult result = rejection.get();
         if (result == OtpVerificationResult.EXPIRED && snapshot.status() == OtpStatus.ACTIVE) {
            this.snapshot = this.snapshot.withStatus(OtpStatus.EXPIRED);
         }
         return result;
      }
      boolean codeMatches = isCodeValid(command, hasher, snapshot);

      if (!codeMatches) {
         final boolean lastAllowedAttempt = snapshot.attempts() + 1 == snapshot.maxAttempts();

         this.snapshot = lastAllowedAttempt
                 ? snapshot.withFailedAttemptAndLock()
                 : snapshot.withAttemptRecorded();

         return lastAllowedAttempt
                 ? OtpVerificationResult.LOCKED
                 : OtpVerificationResult.INVALID_CODE;
      }

      this.snapshot = snapshot.withSuccessfulAttempt(command.now());
      return OtpVerificationResult.VERIFIED;
   }

   public void invalidate() {
      if (snapshot.status() == OtpStatus.ACTIVE) {
         this.snapshot = this.snapshot.withStatus(OtpStatus.INVALIDATED);
      }
   }

   public String getUserId() {
      return userId;
   }
   public OtpSnapshot getSnapshot() {
      return snapshot;
   }

   private static boolean isCodeValid(VerifyOTPCommand command, CodeHasher hasher, OtpSnapshot snapshot) {
      if (snapshot.verificationMode() == OtpVerificationMode.TOTP) {
         return command.authenticatorCodeValid();
      }

      return command.code() != null
              && snapshot.codeHash() != null
              && hasher.matches(command.code(), snapshot.codeHash());
   }
}
