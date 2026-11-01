package dev.amg.payguard.identity.verification.application.command.otp_verification.exception;

import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import java.io.Serial;

public class OtpVerificationException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;
  private final OtpVerificationResult verificationResult;

  public OtpVerificationException(OtpVerificationResult result) {
    super("OTP verification failed: " + result.name().toLowerCase(java.util.Locale.ROOT));
    this.verificationResult = result;
  }

  public OtpVerificationResult result() {
    return verificationResult;
  }
}
