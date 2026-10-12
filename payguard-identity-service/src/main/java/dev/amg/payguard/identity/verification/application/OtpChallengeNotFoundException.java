package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;

public class OtpChallengeNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OtpChallengeNotFoundException(OtpId challengeId) {
    super("OTP challenge not found: " + challengeId);
  }
}
