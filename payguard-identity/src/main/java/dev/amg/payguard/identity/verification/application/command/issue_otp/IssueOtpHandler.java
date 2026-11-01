package dev.amg.payguard.identity.verification.application.command.issue_otp;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.command.issue_otp.exception.InvalidOtpIssuanceConfigurationException;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.OtpRateLimitExceededException;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditAction;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitOperation;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPort;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeGenerator;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the issuance of OTP challenges.
 *
 * <p>Coordinates rate limiting, code generation, challenge creation, persistence, auditing, and
 * delivery.
 *
 * <p>Business invariants remain within the OTP aggregate.
 */
public class IssueOtpHandler {

  private final OtpRepository repository;
  private final OtpCodeGenerator generator;
  private final OtpCodeHasher hasher;
  private final RateLimitPort quota;
  private final OtpDelivery delivery;
  private final VerificationAudit audit;
  private final Supplier<OtpId> ids;
  private final Clock clock;
  private final Duration ttl;
  private final Duration auditRetention;
  private final int maxAttempts;

  public IssueOtpHandler(
      OtpRepository repository,
      OtpCodeGenerator generator,
      OtpCodeHasher hasher,
      RateLimitPort quota,
      OtpDelivery delivery,
      VerificationAudit audit,
      Supplier<OtpId> ids,
      Clock clock,
      Duration ttl,
      Duration auditRetention,
      int maxAttempts) {

    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.generator = Objects.requireNonNull(generator, "generator must not be null");
    this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
    this.quota = Objects.requireNonNull(quota, "quota must not be null");
    this.delivery = Objects.requireNonNull(delivery, "delivery must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
    this.ids = Objects.requireNonNull(ids, "ids must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    validate(ttl, auditRetention, maxAttempts);
    this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    this.auditRetention = Objects.requireNonNull(auditRetention, "auditRetention must not be null");

    this.maxAttempts = maxAttempts;
  }

  /**
   * Issues a new OTP challenge for the requested customer and purpose.
   *
   * @param command the OTP issuance request
   * @return metadata describing the issued challenge
   */
  @Transactional
  public IssueOtpCommandResponse handle(IssueOtpCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    boolean allowed =
        quota
            .acquire(
                new RateLimitRequest(
                    RateLimitOperation.ISSUE,
                    command.customerId().getValue(),
                    null,
                    command.sourceIp(),
                    command.purpose()))
            .permitted();

    if (!allowed) {
      throw new OtpRateLimitExceededException();
    }

    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

    OtpId id = Objects.requireNonNull(ids.get(), "Generated OTP ID must not be null");

    String code =
        Objects.requireNonNull(generator.generate(), "Generated OTP code must not be null");

    if (code.isBlank()) {
      throw new IllegalStateException("Generated OTP code must not be blank");
    }

    String codeHash = hasher.hash(code);

    String deviceBindingHash =
        command.deviceFingerprint() == null ? null : hasher.hash(command.deviceFingerprint());

    OtpSnapshot snapshot =
        new OtpSnapshot(
            command.purpose(),
            command.channel(),
            OtpVerificationMode.RANDOM_NUMERIC,
            codeHash,
            deviceBindingHash,
            maxAttempts,
            now,
            now.plus(ttl),
            now.plus(auditRetention),
            0,
            OtpStatus.ACTIVE,
            null);

    OTP otp = OTP.issue(id, command.customerId(), snapshot);

    repository.replaceActive(otp);

    audit.recordAudit(
        new VerificationAuditEntry(
            id,
            command.customerId(),
            VerificationAuditAction.ISSUED,
            "challenge-issued",
            command.sourceIp(),
            now));

    delivery.submit(
        new OtpDeliveryRequest(
            id,
            command.customerId(),
            command.purpose(),
            command.channel(),
            command.destinationRef(),
            code));

    return IssueOtpCommandResponse.from(otp.getId(), command, otp.getSnapshot().expiresAt());
  }

  private static void validate(Duration ttl, Duration auditRetention, int maxAttempts) {

    if (ttl.isZero() || ttl.isNegative()) {
      throw new InvalidOtpIssuanceConfigurationException("OTP TTL must be positive");
    }

    if (auditRetention.isZero() || auditRetention.isNegative()) {
      throw new InvalidOtpIssuanceConfigurationException("Audit retention must be positive");
    }

    if (maxAttempts <= 0) {
      throw new InvalidOtpIssuanceConfigurationException(
          "Maximum verification attempts must be at least 1");
    }
  }
}
