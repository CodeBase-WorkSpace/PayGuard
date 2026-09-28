package dev.amg.payguard.otp.domain;

import java.time.Instant;

public interface StepUpTokenPort {

  IssuedStepUpToken issue(String userId, OtpPurpose purpose, Instant now);

  boolean consume(String token, String userId, OtpPurpose purpose, Instant now);

  record IssuedStepUpToken(String token, Instant expiresAt) {}
}
