package dev.amg.payguard.otp.domain;

import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface OtpAuditLogPort {

  void append(
      UUID challengeId, String userId, String action, String reason, String sourceIp, Instant at);
}
