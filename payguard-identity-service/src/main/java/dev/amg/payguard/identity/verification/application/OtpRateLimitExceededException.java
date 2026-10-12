package dev.amg.payguard.identity.verification.application;

import java.io.Serial;

public class OtpRateLimitExceededException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public OtpRateLimitExceededException() {
    super("OTP rate limit exceeded");
  }
}
