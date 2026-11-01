package dev.amg.payguard.identity.verification.application.command.otp_verification;

import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.InvalidOtpVerificationCommandException;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.OtpChallengeNotFoundException;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.OtpRateLimitExceededException;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.OtpVerificationException;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.UnsupportedOtpVerificationModeException;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditAction;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitOperation;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPort;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles OTP verification requests.
 *
 * <p>Coordinates rate limiting, locked verification, and auditing. The OTP aggregate owns
 * verification rules and state transitions.
 */
@Service
public class OtpVerificationHandler {

  private final OtpRepository repository;
  private final OtpCodeHasher hasher;
  private final RateLimitPort quota;
  private final VerificationAudit audit;
  private final Clock clock;

  public OtpVerificationHandler(
      OtpRepository repository,
      OtpCodeHasher hasher,
      RateLimitPort quota,
      VerificationAudit audit,
      Clock clock) {

    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.hasher = Objects.requireNonNull(hasher, "hasher must not be null");
    this.quota = Objects.requireNonNull(quota, "quota must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Verifies an existing OTP challenge.
   *
   * <p>Verification runs against the latest persisted state while the challenge is locked. Rejected
   * results are converted to exceptions only after the repository transaction completes.
   *
   * @param command the verification request
   * @return the successful verification response
   * @throws InvalidOtpVerificationCommandException if the request is invalid
   * @throws OtpChallengeNotFoundException if the challenge is unavailable
   * @throws OtpRateLimitExceededException if the verification quota is exhausted
   * @throws OtpVerificationException if verification is rejected
   */
  @Transactional(noRollbackFor = OtpVerificationException.class)
  public OtpVerificationResponse handle(OtpVerificationCommand command) {

    boolean allowed =
        quota
            .acquire(
                new RateLimitRequest(
                    RateLimitOperation.VERIFY,
                    command.customerId().getValue(),
                    null,
                    command.sourceIp(),
                    command.purpose()))
            .permitted();

    if (!allowed) {
      throw new OtpRateLimitExceededException();
    }

    final Function<OTP, VerificationOutcome> verificationOutcomeFunction =
        otp -> {
          if (!otp.getCustomerId().equals(command.customerId())) {
            throw new OtpChallengeNotFoundException(command.otpId());
          }

          if (otp.getSnapshot().verificationMode() != OtpVerificationMode.RANDOM_NUMERIC) {
            throw new UnsupportedOtpVerificationModeException(otp.getSnapshot().verificationMode());
          }

          Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

          final var otpCommand =
              new VerifyOtpCommand(
                  command.purpose(), command.code(), command.deviceFingerprint(), now);

          OtpVerificationResult result = otp.verify(otpCommand, hasher);

          return new VerificationOutcome(result, now);
        };

    final VerificationOutcome outcome =
        repository
            .withLockedChallenge(command.otpId(), verificationOutcomeFunction)
            .orElseThrow(() -> new OtpChallengeNotFoundException(command.otpId()));

    audit.recordAudit(
        new VerificationAuditEntry(
            command.otpId(),
            command.customerId(),
            VerificationAuditAction.VERIFY,
            outcome.result().name().toLowerCase(Locale.ROOT),
            command.sourceIp(),
            outcome.at()));

    if (outcome.result() != OtpVerificationResult.VERIFIED) {
      throw new OtpVerificationException(outcome.result());
    }

    return OtpVerificationResponse.from(command.otpId(), command.purpose(), outcome.at());
  }

  private record VerificationOutcome(OtpVerificationResult result, Instant at) {}
}
