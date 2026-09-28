package dev.amg.payguard.otp.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.amg.payguard.otp.domain.DeliveryPort;
import dev.amg.payguard.otp.domain.OtpChallenge;
import dev.amg.payguard.otp.domain.OtpChallengeRepository;
import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpCodeHasher;
import dev.amg.payguard.otp.domain.OtpPurpose;
import dev.amg.payguard.otp.domain.OtpVerificationResult;
import dev.amg.payguard.otp.domain.RateLimiter;
import dev.amg.payguard.otp.domain.StepUpTokenPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtpApplicationServiceTest {

  private final FixedHasher hasher = new FixedHasher();
  private final InMemoryChallenges challenges = new InMemoryChallenges();
  private final CapturingDelivery delivery = new CapturingDelivery();
  private final StepUpTokenPort tokens =
      new StepUpTokenPort() {
        @Override
        public IssuedStepUpToken issue(String userId, OtpPurpose purpose, Instant now) {
          return new IssuedStepUpToken("step-up-token", now.plusSeconds(180));
        }

        @Override
        public boolean consume(String token, String userId, OtpPurpose purpose, Instant now) {
          return "step-up-token".equals(token);
        }
      };
  private OtpApplicationService service;

  @BeforeEach
  void setUp() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    service =
        new OtpApplicationService(
            challenges,
            hasher,
            () -> "123456",
            (userId, code, at) -> false,
            delivery,
            new AllowAllRateLimiter(),
            tokens,
            event -> {},
            (challengeId, userId, action, reason, sourceIp, at) -> {},
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofDays(90),
            5);
  }

  @Test
  void issueAndVerifyReturnsSingleUseStepUpToken() {
    IssuedOtp issued =
        service.issue(
            new IssueOtpCommand(
                "user-1", OtpPurpose.LOGIN, OtpChannel.SMS, "identity:phone:1", null, "127.0.0.1"));

    assertEquals("123456", delivery.code);
    VerifiedOtp verified =
        service.verify(
            new VerifyOtpCommand(
                issued.challengeId(), "user-1", OtpPurpose.LOGIN, "123456", null, "127.0.0.1"));

    assertEquals("step-up-token", verified.stepUpToken());
    OtpVerificationException replay =
        assertThrows(
            OtpVerificationException.class,
            () ->
                service.verify(
                    new VerifyOtpCommand(
                        issued.challengeId(),
                        "user-1",
                        OtpPurpose.LOGIN,
                        "123456",
                        null,
                        "127.0.0.1")));
    assertEquals(OtpVerificationResult.ALREADY_USED, replay.result());
  }

  @Test
  void wrongPurposeCannotVerifyChallenge() {
    IssuedOtp issued =
        service.issue(
            new IssueOtpCommand(
                "user-1",
                OtpPurpose.LOGIN,
                OtpChannel.EMAIL,
                "identity:email:1",
                null,
                "127.0.0.1"));

    OtpVerificationException exception =
        assertThrows(
            OtpVerificationException.class,
            () ->
                service.verify(
                    new VerifyOtpCommand(
                        issued.challengeId(),
                        "user-1",
                        OtpPurpose.LOAN_DISBURSE,
                        "123456",
                        null,
                        "127.0.0.1")));
    assertEquals(OtpVerificationResult.PURPOSE_MISMATCH, exception.result());
  }

  private static final class FixedHasher implements OtpCodeHasher {
    @Override
    public String hash(String value) {
      return "hash:" + value;
    }

    @Override
    public boolean matches(String value, String encodedHash) {
      return encodedHash.equals(hash(value));
    }
  }

  private static final class CapturingDelivery implements DeliveryPort {
    private String code;

    @Override
    public void deliver(DeliveryMessage message) {
      code = message.code();
    }
  }

  private static final class AllowAllRateLimiter implements RateLimiter {
    @Override
    public boolean allowIssue(String userId, OtpPurpose purpose, String sourceIp) {
      return true;
    }

    @Override
    public boolean allowVerify(String userId, OtpPurpose purpose, String sourceIp) {
      return true;
    }
  }

  private static final class InMemoryChallenges implements OtpChallengeRepository {
    private final Map<UUID, OtpChallenge> values = new HashMap<>();

    @Override
    public void invalidateActive(String userId, OtpPurpose purpose) {
      values.values().stream()
          .filter(challenge -> challenge.userId().equals(userId) && challenge.purpose() == purpose)
          .forEach(OtpChallenge::invalidate);
    }

    @Override
    public OtpChallenge save(OtpChallenge challenge) {
      values.put(challenge.id(), challenge);
      return challenge;
    }

    @Override
    public Optional<OtpChallenge> findById(UUID challengeId) {
      return Optional.ofNullable(values.get(challengeId));
    }
  }
}
