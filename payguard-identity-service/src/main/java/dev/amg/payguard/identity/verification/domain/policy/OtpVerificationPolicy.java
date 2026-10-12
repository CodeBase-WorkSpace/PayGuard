package dev.amg.payguard.identity.verification.domain.policy;

import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.util.Optional;

public final class OtpVerificationPolicy {
  private OtpVerificationPolicy() {}

  /** Guards that reject before an attempt is counted. Empty = eligible to be checked. */
  public static Optional<OtpVerificationResult> findRejection(
      VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
    if (command.purpose() != snapshot.purpose()) {
      return Optional.of(OtpVerificationResult.PURPOSE_MISMATCH);
    }
    return switch (snapshot.status()) {
      case USED -> Optional.of(OtpVerificationResult.ALREADY_USED);
      case LOCKED -> Optional.of(OtpVerificationResult.LOCKED);
      case INVALIDATED -> Optional.of(OtpVerificationResult.INVALIDATED);
      case EXPIRED -> Optional.of(OtpVerificationResult.EXPIRED);
      case ACTIVE -> evaluateActiveStateGuards(command, snapshot, hasher);
    };
  }

  private static Optional<OtpVerificationResult> evaluateActiveStateGuards(
      VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
    if (command.now().isBefore(snapshot.createdAt())) {
      throw new IllegalArgumentException("verification time must not precede challenge creation");
    }
    if (!command.now().isBefore(snapshot.expiresAt())) {
      return Optional.of(OtpVerificationResult.EXPIRED);
    }

    if (snapshot.deviceBindingHash() != null) {
      var matches =
          command.deviceFingerprint() != null
              && hasher.matches(command.deviceFingerprint(), snapshot.deviceBindingHash());
      if (!matches) {
        return Optional.of(OtpVerificationResult.DEVICE_MISMATCH);
      }
    }
    return Optional.empty();
  }
}
