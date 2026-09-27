package dev.amg.payguard.otp.domain;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface OtpEventPublisher {

  void publish(OtpEvent event);

  record OtpEvent(
      String type,
      UUID challengeId,
      String userId,
      OtpPurpose purpose,
      OtpChannel channel,
      OtpVerificationResult result,
      Instant occurredAt,
      String reason) {}
}
