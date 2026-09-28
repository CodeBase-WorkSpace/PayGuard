package dev.amg.payguard.otp.infrastructure.persistence;

import dev.amg.payguard.otp.domain.OtpAuditLogPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaOtpAuditLogAdapter implements OtpAuditLogPort {

  private final OtpAuditLogJpaRepository repository;

  public JpaOtpAuditLogAdapter(OtpAuditLogJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void append(
      UUID challengeId, String userId, String action, String reason, String sourceIp, Instant at) {
    repository.save(new OtpAuditLogEntity(challengeId, userId, action, reason, sourceIp, at));
  }
}
