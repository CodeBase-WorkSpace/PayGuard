package dev.amg.payguard.identity.verification.infrastructure.persistence;

import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_audit_log")
public class OtpAuditLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "challenge_id", nullable = false)
  private String challengeId;

  @Column(name = "user_id", nullable = false, length = 255)
  private String userId;

  @Column(nullable = false, length = 32)
  private String action;

  @Column(nullable = false, length = 128)
  private String reason;

  @Column(name = "source_ip", length = 64)
  private String sourceIp;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected OtpAuditLogEntity() {}

  OtpAuditLogEntity(VerificationAuditEntry entry) {
    this.challengeId = entry.challengeId().value();
    this.userId = entry.customerId().getValue();
    this.action = entry.action().name();
    this.reason = entry.reasonCode();
    this.sourceIp = entry.sourceIp();
    this.occurredAt = entry.occurredAt();
  }
}
