package dev.amg.payguard.otp.application;

import dev.amg.payguard.otp.domain.AuthenticatorCodeVerifier;
import dev.amg.payguard.otp.domain.DeliveryPort;
import dev.amg.payguard.otp.domain.OtpAuditLogPort;
import dev.amg.payguard.otp.domain.OtpChallenge;
import dev.amg.payguard.otp.domain.OtpChallengeRepository;
import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpCodeGenerator;
import dev.amg.payguard.otp.domain.OtpCodeHasher;
import dev.amg.payguard.otp.domain.OtpEventPublisher;
import dev.amg.payguard.otp.domain.OtpVerificationMode;
import dev.amg.payguard.otp.domain.OtpVerificationResult;
import dev.amg.payguard.otp.domain.RateLimiter;
import dev.amg.payguard.otp.domain.StepUpTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class OtpApplicationService {

  private final OtpChallengeRepository challenges;
  private final OtpCodeHasher hasher;
  private final OtpCodeGenerator codeGenerator;
  private final AuthenticatorCodeVerifier authenticatorCodeVerifier;
  private final DeliveryPort delivery;
  private final RateLimiter rateLimiter;
  private final StepUpTokenPort stepUpTokens;
  private final OtpEventPublisher events;
  private final OtpAuditLogPort audit;
  private final Clock clock;
  private final Duration randomTtl;
  private final Duration totpTtl;
  private final Duration auditRetention;
  private final int maxAttempts;

  public OtpApplicationService(
      OtpChallengeRepository challenges,
      OtpCodeHasher hasher,
      OtpCodeGenerator codeGenerator,
      AuthenticatorCodeVerifier authenticatorCodeVerifier,
      DeliveryPort delivery,
      RateLimiter rateLimiter,
      StepUpTokenPort stepUpTokens,
      OtpEventPublisher events,
      OtpAuditLogPort audit,
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
    this.stepUpTokens = stepUpTokens;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
    this.randomTtl = randomTtl;
    this.totpTtl = totpTtl;
    this.auditRetention = auditRetention;
    this.maxAttempts = maxAttempts;
  }

  public IssuedOtp issue(IssueOtpCommand command) {
    requireText(command.userId(), "userId");
    if (!rateLimiter.allowIssue(command.userId(), command.purpose(), command.sourceIp())) {
      throw new OtpRateLimitExceededException();
    }
    Instant now = clock.instant();
    boolean totp = command.channel() == OtpChannel.AUTHENTICATOR_APP;
    Duration ttl = totp ? totpTtl : randomTtl;
    UUID challengeId = UUID.randomUUID();
    String code = totp ? null : codeGenerator.generate();
    OtpChallenge challenge =
        OtpChallenge.issue(
            challengeId,
            command.userId(),
            command.purpose(),
            command.channel(),
            totp ? OtpVerificationMode.TOTP : OtpVerificationMode.RANDOM_NUMERIC,
            code == null ? null : hasher.hash(code),
            command.deviceFingerprint() == null ? null : hasher.hash(command.deviceFingerprint()),
            maxAttempts,
            now,
            now.plus(ttl),
            now.plus(auditRetention));
    challenges.invalidateActive(command.userId(), command.purpose());
    challenges.save(challenge);
    if (!totp) {
      delivery.deliver(
          new DeliveryPort.DeliveryMessage(
              challengeId,
              command.userId(),
              command.purpose(),
              command.channel(),
              command.destinationRef(),
              code));
    }
    publish(
        new OtpEventPublisher.OtpEvent(
            "otp.challenge-issued",
            challengeId,
            command.userId(),
            command.purpose(),
            command.channel(),
            null,
            now,
            "challenge-issued"));
    audit.append(
        challengeId, command.userId(), "ISSUED", "challenge-issued", command.sourceIp(), now);
    return new IssuedOtp(challengeId, command.purpose(), command.channel(), challenge.expiresAt());
  }

  public VerifiedOtp verify(VerifyOtpCommand command) {
    OtpChallenge challenge =
        challenges
            .findById(command.challengeId())
            .orElseThrow(() -> new OtpChallengeNotFoundException(command.challengeId()));
    if (!challenge.userId().equals(command.userId())) {
      throw new OtpChallengeNotFoundException(command.challengeId());
    }
    if (!rateLimiter.allowVerify(command.userId(), command.purpose(), command.sourceIp())) {
      throw new OtpRateLimitExceededException();
    }
    Instant now = clock.instant();
    boolean authenticatorValid =
        challenge.verificationMode() == OtpVerificationMode.TOTP
            && authenticatorCodeVerifier.verify(command.userId(), command.code(), now);
    OtpVerificationResult result =
        challenge.verify(
            command.purpose(),
            command.code(),
            command.deviceFingerprint(),
            hasher,
            authenticatorValid,
            now);
    challenges.save(challenge);
    String reason = result.name().toLowerCase(java.util.Locale.ROOT);
    audit.append(challenge.id(), command.userId(), "VERIFY", reason, command.sourceIp(), now);
    if (result != OtpVerificationResult.VERIFIED) {
      publish(
          new OtpEventPublisher.OtpEvent(
              result == OtpVerificationResult.LOCKED ? "otp.locked-out" : "otp.failed",
              challenge.id(),
              command.userId(),
              command.purpose(),
              challenge.channel(),
              result,
              now,
              reason));
      throw new OtpVerificationException(result);
    }
    StepUpTokenPort.IssuedStepUpToken token =
        stepUpTokens.issue(command.userId(), command.purpose(), now);
    publish(
        new OtpEventPublisher.OtpEvent(
            "otp.verified",
            challenge.id(),
            command.userId(),
            command.purpose(),
            challenge.channel(),
            result,
            now,
            "verified"));
    return new VerifiedOtp(token.token(), command.purpose(), token.expiresAt());
  }

  private void publish(OtpEventPublisher.OtpEvent event) {
    events.publish(event);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
