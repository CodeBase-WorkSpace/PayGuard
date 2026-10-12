package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import dev.amg.payguard.identity.verification.application.port.TotpVerification;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import dev.amg.payguard.identity.verification.application.port.VerificationQuota;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.event.OtpEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeGenerator;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public final class OtpApplicationService {

  private final OtpRepository challenges;
  private final OtpCodeHasher hasher;
  private final OtpCodeGenerator codeGenerator;
  private final TotpVerification authenticatorCodeVerifier;
  private final OtpDelivery delivery;
  private final VerificationQuota rateLimiter;
  private final Supplier<OtpId> ids;
  private final OtpEventPublisher events;
  private final VerificationAudit audit;
  private final Clock clock;
  private final Duration randomTtl;
  private final Duration totpTtl;
  private final Duration auditRetention;
  private final int maxAttempts;

  public OtpApplicationService(
      OtpRepository challenges,
      OtpCodeHasher hasher,
      OtpCodeGenerator codeGenerator,
      TotpVerification authenticatorCodeVerifier,
      OtpDelivery delivery,
      VerificationQuota rateLimiter,
      Supplier<OtpId> ids,
      OtpEventPublisher events,
      VerificationAudit audit,
      Clock clock,
      Duration randomTtl,
      Duration totpTtl,
      Duration auditRetention,
      int maxAttempts) {
    this.challenges = challenges;
    this.hasher = hasher;
    this.codeGenerator = codeGenerator;
    this.authenticatorCodeVerifier = authenticatorCodeVerifier;
    this.delivery = delivery;
    this.rateLimiter = rateLimiter;
    this.ids = ids;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
    this.randomTtl = randomTtl;
    this.totpTtl = totpTtl;
    this.auditRetention = auditRetention;
    this.maxAttempts = maxAttempts;
  }

  public IssuedOtp issue(IssueOtpCommand command) {
    Objects.requireNonNull(command.customerId(), "customerId must not be null");
    if (!rateLimiter.tryAcquire(
        new VerificationQuota.Request(
            command.customerId(),
            command.purpose(),
            command.sourceIp(),
            VerificationQuota.Operation.ISSUE))) {
      throw new OtpRateLimitExceededException();
    }
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    boolean totp = command.channel() == OtpChannel.AUTHENTICATOR_APP;
    Duration ttl = totp ? totpTtl : randomTtl;
    OtpId challengeId = ids.get();
    String code = totp ? null : codeGenerator.generate();
    OTP challenge =
        OTP.issue(
            challengeId,
            command.customerId(),
            new OtpSnapshot(
                command.purpose(),
                command.channel(),
                totp ? OtpVerificationMode.TOTP : OtpVerificationMode.RANDOM_NUMERIC,
                code == null ? null : hasher.hash(code),
                command.deviceFingerprint() == null
                    ? null
                    : hasher.hash(command.deviceFingerprint()),
                maxAttempts,
                now,
                now.plus(ttl),
                now.plus(auditRetention),
                0,
                OtpStatus.ACTIVE,
                null));
    challenges.replaceActive(challenge);
    if (!totp) {
      delivery.submit(
          new OtpDeliveryRequest(
              challengeId,
              command.customerId(),
              command.purpose(),
              command.channel(),
              command.destinationRef(),
              code));
    }
    publish(
        new OtpEvent(
            "identity.challenge-issued",
            challengeId,
            command.customerId(),
            command.purpose(),
            command.channel(),
            null,
            now,
            "challenge-issued"));
    audit.record(
        new VerificationAuditEntry(
            challengeId,
            command.customerId(),
            VerificationAuditEntry.Action.ISSUED,
            "challenge-issued",
            command.sourceIp(),
            now));
    return new IssuedOtp(
        challengeId, command.purpose(), command.channel(), challenge.getSnapshot().expiresAt());
  }

  public VerifiedOtp verify(OtpVerificationCommand command) {
    OTP challenge =
        challenges
            .findById(command.challengeId())
            .orElseThrow(
                () ->
                    new dev.amg.payguard.identity.verification.application
                        .OtpChallengeNotFoundException(command.challengeId()));
    if (!challenge.getCustomerId().equals(command.customerId())) {
      throw new dev.amg.payguard.identity.verification.application.OtpChallengeNotFoundException(
          command.challengeId());
    }
    if (!rateLimiter.tryAcquire(
        new VerificationQuota.Request(
            command.customerId(),
            command.purpose(),
            command.sourceIp(),
            VerificationQuota.Operation.VERIFY))) {
      throw new OtpRateLimitExceededException();
    }
    Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    boolean authenticatorValid =
        challenge.getSnapshot().verificationMode() == OtpVerificationMode.TOTP
            && challenge.getSnapshot().status() == OtpStatus.ACTIVE
            && command.purpose() == challenge.getSnapshot().purpose()
            && now.isBefore(challenge.getSnapshot().expiresAt())
            && authenticatorCodeVerifier.verify(command.customerId(), command.code(), now);
    VerificationOutcome outcome =
        challenges
            .withLockedChallenge(
                command.challengeId(),
                current -> {
                  if (!current.getCustomerId().equals(command.customerId())) {
                    throw new OtpChallengeNotFoundException(command.challengeId());
                  }
                  Instant lockedAt =
                      clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                  OtpVerificationResult currentResult =
                      current.verify(
                          new VerifyOtpCommand(
                              command.purpose(),
                              command.code(),
                              command.deviceFingerprint(),
                              lockedAt,
                              authenticatorValid),
                          hasher);
                  return new VerificationOutcome(current, currentResult, lockedAt);
                })
            .orElseThrow(() -> new OtpChallengeNotFoundException(command.challengeId()));
    challenge = outcome.challenge();
    now = outcome.at();
    OtpVerificationResult result = outcome.result();
    String reason = result.name().toLowerCase(java.util.Locale.ROOT);
    audit.record(
        new VerificationAuditEntry(
            challenge.getId(),
            command.customerId(),
            VerificationAuditEntry.Action.VERIFY,
            reason,
            command.sourceIp(),
            now));
    if (result != OtpVerificationResult.VERIFIED) {
      publish(
          new OtpEvent(
              result == OtpVerificationResult.LOCKED ? "identity.locked-out" : "identity.failed",
              challenge.getId(),
              command.customerId(),
              command.purpose(),
              challenge.getSnapshot().channel(),
              result,
              now,
              reason));
      throw new dev.amg.payguard.identity.verification.application.OtpVerificationException(result);
    }
    publish(
        new OtpEvent(
            "identity.verified",
            challenge.getId(),
            command.customerId(),
            command.purpose(),
            challenge.getSnapshot().channel(),
            result,
            now,
            "verified"));
    return new VerifiedOtp(challenge.getId(), command.purpose(), now);
  }

  private record VerificationOutcome(OTP challenge, OtpVerificationResult result, Instant at) {}

  private void publish(OtpEvent event) {
    events.publish(event);
  }
}
