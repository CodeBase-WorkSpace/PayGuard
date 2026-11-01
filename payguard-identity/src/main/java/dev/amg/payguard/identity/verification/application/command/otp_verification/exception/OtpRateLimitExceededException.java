package dev.amg.payguard.identity.verification.application.command.otp_verification.exception;

import java.io.Serial;

public class OtpRateLimitExceededException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public OtpRateLimitExceededException() {
    super("OTP rate limit exceeded");
  }
}
