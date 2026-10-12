package dev.amg.payguard.identity.verification.infrastructure.persistence;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "identity_challenge")
public class OtpChallengeEntity {

  @Id
  @Column(length = 26)
  private String id;

  @Column(name = "user_id", nullable = false, length = 255)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OtpPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OtpChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_mode", nullable = false, length = 32)
  private OtpVerificationMode verificationMode;

  @Column(name = "code_hash", length = 512)
  private String codeHash;

  @Column(name = "device_binding_hash", length = 512)
  private String deviceBindingHash;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OtpStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "retention_until", nullable = false)
  private Instant retentionUntil;

  protected OtpChallengeEntity() {}

  private OtpChallengeEntity(OTP challenge) {
    copyFrom(challenge);
  }

  static OtpChallengeEntity from(OTP challenge) {
    return new OtpChallengeEntity(challenge);
  }

  final void copyFrom(OTP challenge) {
    id = challenge.getId().value();
    userId = challenge.getCustomerId().getValue();
    purpose = challenge.getSnapshot().purpose();
    channel = challenge.getSnapshot().channel();
    verificationMode = challenge.getSnapshot().verificationMode();
    codeHash = challenge.getSnapshot().codeHash();
    deviceBindingHash = challenge.getSnapshot().deviceBindingHash();
    attempts = challenge.getSnapshot().attempts();
    maxAttempts = challenge.getSnapshot().maxAttempts();
    status = challenge.getSnapshot().status();
    createdAt = challenge.getSnapshot().createdAt();
    expiresAt = challenge.getSnapshot().expiresAt();
    consumedAt = challenge.getSnapshot().consumedAt();
    retentionUntil = challenge.getSnapshot().retentionUntil();
  }

  OTP toDomain() {
    return OTP.restore(
        OtpId.parse(id),
        CustomerId.parse(userId),
        OtpSnapshot.create(
            purpose,
            channel,
            verificationMode,
            codeHash,
            deviceBindingHash,
            maxAttempts,
            createdAt,
            expiresAt,
            retentionUntil,
            attempts,
            status,
            consumedAt));
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public OtpPurpose getPurpose() {
    return purpose;
  }

  public OtpStatus getStatus() {
    return status;
  }
}
