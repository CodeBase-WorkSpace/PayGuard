package dev.amg.payguard.otp.application;

import java.util.UUID;

public class OtpChallengeNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OtpChallengeNotFoundException(UUID challengeId) {
    super("OTP challenge not found: " + challengeId);
  }
}
