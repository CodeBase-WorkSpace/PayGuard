package dev.amg.payguard.identity.verification.application.command.otp_verification;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;
import java.util.Objects;

/**
 * Successful completion of an OTP challenge.
 *
 * <p>Does not represent authorization to perform a financial operation.
 */
public record OtpVerificationResponse(OtpId challengeId, OtpPurpose purpose, Instant verifiedAt) {

  public OtpVerificationResponse {
    Objects.requireNonNull(challengeId, "challengeId must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
  }

  public static OtpVerificationResponse from(
      OtpId challengeId, OtpPurpose purpose, Instant verifiedAt) {

    return new OtpVerificationResponse(challengeId, purpose, verifiedAt);
  }
}
