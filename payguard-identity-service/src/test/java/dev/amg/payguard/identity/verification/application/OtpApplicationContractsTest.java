package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import dev.amg.payguard.identity.verification.application.port.TotpVerification;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import dev.amg.payguard.identity.verification.application.port.VerificationQuota;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OtpApplicationContractsTest {
  private static final CustomerId CUSTOMER = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");
  private static final OtpId ID = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final OtpRepository repository = mock(OtpRepository.class);
  private final OtpDelivery delivery = mock(OtpDelivery.class);
  private final VerificationAudit audit = mock(VerificationAudit.class);
  private final VerificationQuota quota = mock(VerificationQuota.class);
  private final TotpVerification totp = mock(TotpVerification.class);
  private final OtpEventPublisher events = mock(OtpEventPublisher.class);
  private OtpApplicationService service;

  @BeforeEach
  void setUp() {
    when(quota.tryAcquire(any())).thenReturn(true);
    service =
        new OtpApplicationService(
            repository,
            new HmacOtpCodeHasher("a-secure-test-pepper-with-at-least-32-chars"),
            () -> "123456",
            totp,
            delivery,
            quota,
            () -> ID,
            events,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            Duration.ofDays(1097),
            5);
  }

  @Test
  void savesBeforeDeliveryAndAuditsWithoutSecrets() {
    service.issue(issue(OtpChannel.SMS));
    var order = inOrder(repository, delivery, events, audit);
    order.verify(repository).replaceActive(any(OTP.class));
    var payload = ArgumentCaptor.forClass(OtpDeliveryRequest.class);
    order.verify(delivery).submit(payload.capture());
    order.verify(events).publish(any());
    var entry = ArgumentCaptor.forClass(VerificationAuditEntry.class);
    order.verify(audit).record(entry.capture());
    assertEquals("123456", payload.getValue().code());
    assertEquals("identity:phone:1", payload.getValue().destinationRef());
    assertFalse(payload.getValue().toString().contains("123456"));
    assertEquals(CUSTOMER, entry.getValue().customerId());
    assertEquals(ID, entry.getValue().challengeId());
    assertEquals(VerificationAuditEntry.Action.ISSUED, entry.getValue().action());
    assertFalse(entry.getValue().toString().contains("123456"));
  }

  @Test
  void deliveryFailurePropagatesAfterPersistenceWithoutAuditOrEvent() {
    var failure = new IllegalStateException("provider unavailable");
    doThrow(failure).when(delivery).submit(any());
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> service.issue(issue(OtpChannel.SMS))));
    verify(repository).replaceActive(any());
    verifyNoInteractions(audit, events);
  }

  @Test
  void issuanceAuditFailurePropagatesAfterDelivery() {
    var failure = new IllegalStateException("audit unavailable");
    doThrow(failure).when(audit).record(any());
    assertSame(
        failure,
        assertThrows(IllegalStateException.class, () -> service.issue(issue(OtpChannel.SMS))));
    verify(delivery).submit(any());
    verify(repository).replaceActive(any());
  }

  @Test
  void persistenceFailureStopsDeliveryAndAudit() {
    doThrow(new IllegalStateException("database unavailable"))
        .when(repository)
        .replaceActive(any());
    assertThrows(IllegalStateException.class, () -> service.issue(issue(OtpChannel.SMS)));
    verifyNoInteractions(delivery, audit, events);
  }

  @Test
  void quotaDenialStopsIssuance() {
    when(quota.tryAcquire(any())).thenReturn(false);
    assertThrows(OtpRateLimitExceededException.class, () -> service.issue(issue(OtpChannel.SMS)));
    verifyNoInteractions(repository, delivery, audit, events);
  }

  @Test
  void wrongCustomerCannotReachQuotaOrVerification() {
    OTP otp = issuedChallenge(OtpChannel.SMS);
    clearInvocations(quota, repository, audit, delivery, events);
    assertThrows(
        OtpChallengeNotFoundException.class,
        () ->
            service.verify(
                new OtpVerificationCommand(
                    ID,
                    CustomerId.parse("cus_ZBCDEFGHIJKLMNOPQRSTUV"),
                    OtpPurpose.LOGIN,
                    "123456",
                    null,
                    "127.0.0.1")));
    verifyNoInteractions(quota, totp, audit);
    assertEquals(0, otp.getSnapshot().attempts());
  }

  @Test
  void verificationAuditFailureDoesNotReturnSuccessOrPublishVerifiedEvent() {
    OTP otp = issuedChallenge(OtpChannel.SMS);
    clearInvocations(events);
    doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
    assertThrows(IllegalStateException.class, () -> service.verify(verification("123456")));
    verify(repository).withLockedChallenge(eq(ID), any());
    assertEquals(OtpStatus.USED, otp.getSnapshot().status());
    verifyNoInteractions(events);
  }

  @Test
  void failedVerificationPersistenceStopsAuditAndSuccessResponse() {
    issuedChallenge(OtpChannel.SMS);
    clearInvocations(audit, events);
    doThrow(new IllegalStateException("database unavailable"))
        .when(repository)
        .withLockedChallenge(eq(ID), any());
    assertThrows(IllegalStateException.class, () -> service.verify(verification("123456")));
    verifyNoInteractions(audit, events);
  }

  @Test
  void totpUsesTrustedVerifierAndNeverTreatsAnArbitraryCodeAsSuccess() {
    OTP otp = issuedChallenge(OtpChannel.AUTHENTICATOR_APP);
    verifyNoInteractions(delivery);
    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        assertThrows(OtpVerificationException.class, () -> service.verify(verification("123456")))
            .result());
    verify(totp).verify(CUSTOMER, "123456", NOW);
    assertEquals(1, otp.getSnapshot().attempts());
    when(totp.verify(CUSTOMER, "123456", NOW)).thenReturn(true);
    assertEquals(ID, service.verify(verification("123456")).challengeId());
  }

  @Test
  void failedAttemptIsSavedAndAuditedBeforeRejection() {
    OTP otp = issuedChallenge(OtpChannel.SMS);
    clearInvocations(repository, audit, events);
    assertEquals(
        OtpVerificationResult.INVALID_CODE,
        assertThrows(OtpVerificationException.class, () -> service.verify(verification("000000")))
            .result());
    var order = inOrder(repository, audit, events);
    order.verify(repository).findById(ID);
    order.verify(repository).withLockedChallenge(eq(ID), any());
    order
        .verify(audit)
        .record(
            new VerificationAuditEntry(
                ID,
                CUSTOMER,
                VerificationAuditEntry.Action.VERIFY,
                "invalid_code",
                "127.0.0.1",
                NOW));
    order.verify(events).publish(any());
  }

  @Test
  void authenticatorDependencyFailureDoesNotConsumeAttemptOrPersistInvalidCode() {
    OTP otp = issuedChallenge(OtpChannel.AUTHENTICATOR_APP);
    clearInvocations(repository, audit, events);
    when(totp.verify(CUSTOMER, "123456", NOW))
        .thenThrow(new infrastructure.vault.VaultUnavailableException());
    assertThrows(
        infrastructure.vault.VaultUnavailableException.class,
        () -> service.verify(verification("123456")));
    assertEquals(0, otp.getSnapshot().attempts());
    assertEquals(OtpStatus.ACTIVE, otp.getSnapshot().status());
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
        .withLockedChallenge(eq(ID), any());
    verifyNoInteractions(audit, events);
  }

  private OTP issuedChallenge(OtpChannel channel) {
    service.issue(issue(channel));
    var capture = ArgumentCaptor.forClass(OTP.class);
    verify(repository).replaceActive(capture.capture());
    when(repository.findById(ID)).thenReturn(Optional.of(capture.getValue()));
    when(repository.withLockedChallenge(eq(ID), any()))
        .thenAnswer(
            invocation -> {
              java.util.function.Function<OTP, Object> mutation = invocation.getArgument(1);
              return Optional.of(mutation.apply(capture.getValue()));
            });
    return capture.getValue();
  }

  private static IssueOtpCommand issue(OtpChannel channel) {
    return new IssueOtpCommand(
        CUSTOMER, OtpPurpose.LOGIN, channel, "identity:phone:1", null, "127.0.0.1");
  }

  private static OtpVerificationCommand verification(String code) {
    return new OtpVerificationCommand(ID, CUSTOMER, OtpPurpose.LOGIN, code, null, "127.0.0.1");
  }
}
