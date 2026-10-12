package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;

public class OtpVerificationException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private final OtpVerificationResult verificationResult;

  public OtpVerificationException(OtpVerificationResult result) {
    super("OTP verification failed: " + result.name().toLowerCase(java.util.Locale.ROOT));
    this.verificationResult = result;
  }

  public OtpVerificationResult result() {
    return verificationResult;
  }
}
