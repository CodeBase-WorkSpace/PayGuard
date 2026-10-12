package dev.amg.payguard.identity.verification.infrastructure.persistence;

import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaOtpAuditLogAdapter implements VerificationAudit {

  private final OtpAuditLogJpaRepository repository;

  public JpaOtpAuditLogAdapter(OtpAuditLogJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void record(VerificationAuditEntry entry) {
    repository.save(new OtpAuditLogEntity(entry));
  }
}
