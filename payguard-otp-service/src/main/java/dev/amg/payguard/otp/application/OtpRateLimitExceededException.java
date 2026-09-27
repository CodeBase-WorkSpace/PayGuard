package dev.amg.payguard.otp.application;

public class OtpRateLimitExceededException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OtpRateLimitExceededException() {
    super("OTP rate limit exceeded");
  }
}
