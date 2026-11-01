package dev.amg.payguard.identity.verification.application.command.otp_verification.exception;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import java.io.Serial;

public class OtpChallengeNotFoundException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public OtpChallengeNotFoundException(OtpId challengeId) {
    super("OTP challenge not found: " + challengeId);
  }
}
