package dev.amg.payguard.otp.infrastructure.persistence;

import dev.amg.payguard.otp.domain.OtpChallenge;
import dev.amg.payguard.otp.domain.OtpChallengeStatus;
import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpPurpose;
import dev.amg.payguard.otp.domain.OtpVerificationMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenge")
public class OtpChallengeEntity {

  @Id private UUID id;

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
  private OtpChallengeStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "retention_until", nullable = false)
  private Instant retentionUntil;

  protected OtpChallengeEntity() {}

  private OtpChallengeEntity(OtpChallenge challenge) {
    copyFrom(challenge);
  }

  static OtpChallengeEntity from(OtpChallenge challenge) {
    return new OtpChallengeEntity(challenge);
  }

  final void copyFrom(OtpChallenge challenge) {
    id = challenge.id();
    userId = challenge.userId();
    purpose = challenge.purpose();
    channel = challenge.channel();
    verificationMode = challenge.verificationMode();
    codeHash = challenge.codeHash();
    deviceBindingHash = challenge.deviceBindingHash();
    attempts = challenge.attempts();
    maxAttempts = challenge.maxAttempts();
    status = challenge.status();
    createdAt = challenge.createdAt();
    expiresAt = challenge.expiresAt();
    consumedAt = challenge.consumedAt();
    retentionUntil = challenge.retentionUntil();
  }

  OtpChallenge toDomain() {
    return OtpChallenge.restore(
        id,
        userId,
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
        consumedAt);
  }

  public UUID getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public OtpPurpose getPurpose() {
    return purpose;
  }

  public OtpChallengeStatus getStatus() {
    return status;
  }
}
