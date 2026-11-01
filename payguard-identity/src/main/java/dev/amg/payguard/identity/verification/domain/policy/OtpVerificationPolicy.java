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
    if (!command.purpose().equals(snapshot.purpose())) {
      return Optional.of(OtpVerificationResult.PURPOSE_MISMATCH);
    }
    return snapshot.status().rejection(command, snapshot, hasher);
  }
}
