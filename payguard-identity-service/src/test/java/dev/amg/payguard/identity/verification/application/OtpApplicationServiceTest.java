package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import dev.amg.payguard.identity.verification.application.port.VerificationQuota;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OtpApplicationServiceTest {

  private final FixedHasher hasher = new FixedHasher();
  private final InMemoryChallenges challenges = new InMemoryChallenges();
  private final CapturingDelivery delivery = new CapturingDelivery();
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
            new AllowAllVerificationQuota(),
            () -> OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV"),
            event -> {},
            entry -> {},
            Clock.fixed(now, ZoneOffset.UTC),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofDays(1097),
            5);
  }

  @Test
  void issueAndVerifyReturnsCompletedChallengeAndRejectsReplay() {
    IssuedOtp issued =
        service.issue(
            new IssueOtpCommand(
                CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
                OtpPurpose.LOGIN,
                OtpChannel.SMS,
                "identity:phone:1",
                null,
                "127.0.0.1"));

    assertEquals("123456", delivery.code);
    VerifiedOtp verified =
        service.verify(
            new OtpVerificationCommand(
                issued.challengeId(),
                CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
                OtpPurpose.LOGIN,
                "123456",
                null,
                "127.0.0.1"));

    assertEquals(issued.challengeId(), verified.challengeId());
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), verified.verifiedAt());
    OtpVerificationException replay =
        assertThrows(
            OtpVerificationException.class,
            () ->
                service.verify(
                    new OtpVerificationCommand(
                        issued.challengeId(),
                        CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
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
                CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
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
                    new OtpVerificationCommand(
                        issued.challengeId(),
                        CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
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

  private static final class CapturingDelivery implements OtpDelivery {
    private String code;

    @Override
    public void submit(OtpDeliveryRequest message) {
      code = message.code();
    }
  }

  private static final class AllowAllVerificationQuota implements VerificationQuota {
    @Override
    public boolean tryAcquire(VerificationQuota.Request request) {
      return true;
    }
  }

  private static final class InMemoryChallenges implements OtpRepository {
    @Override
    public <T> Optional<T> withLockedChallenge(
        OtpId id, java.util.function.Function<OTP, T> mutation) {
      return findById(id).map(mutation);
    }

    private final Map<OtpId, OTP> values = new HashMap<>();

    @Override
    public void replaceActive(OTP replacement) {
      values.values().stream()
          .filter(
              challenge ->
                  challenge.getCustomerId().equals(replacement.getCustomerId())
                      && challenge.getSnapshot().purpose() == replacement.getSnapshot().purpose())
          .forEach(OTP::invalidate);
      save(replacement);
    }

    @Override
    public void save(OTP challenge) {
      values.put(challenge.getId(), challenge);
    }

    @Override
    public Optional<OTP> findById(OtpId challengeId) {
      return Optional.ofNullable(values.get(challengeId));
    }
  }
}
