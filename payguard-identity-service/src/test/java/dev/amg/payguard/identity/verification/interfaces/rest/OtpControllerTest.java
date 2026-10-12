package dev.amg.payguard.identity.verification.interfaces.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.IssueOtpCommand;
import dev.amg.payguard.identity.verification.application.IssuedOtp;
import dev.amg.payguard.identity.verification.application.OtpApplicationService;
import dev.amg.payguard.identity.verification.application.OtpVerificationCommand;
import dev.amg.payguard.identity.verification.application.VerifiedOtp;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OtpControllerTest {
  @Test
  void parsesCustomerAndChallengeAtBoundaryAndReturnsStringIds() {
    var service = mock(OtpApplicationService.class);
    var request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    OtpId id = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
    CustomerId customer = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    when(service.issue(any())).thenReturn(new IssuedOtp(id, OtpPurpose.LOGIN, OtpChannel.SMS, now));
    when(service.verify(any())).thenReturn(new VerifiedOtp(id, OtpPurpose.LOGIN, now));
    var controller = new OtpController(service);
    assertEquals(
        id.value(),
        controller
            .issue(
                new OtpController.IssueOtpRequest(
                    customer.getValue(), OtpPurpose.LOGIN, OtpChannel.SMS, "phone-ref", null),
                request)
            .getBody()
            .challengeId());
    assertEquals(
        id.value(),
        controller
            .verify(
                id.value(),
                new OtpController.VerifyOtpRequest(
                    customer.getValue(), OtpPurpose.LOGIN, "123456", null),
                request)
            .getBody()
            .challengeId());
    var issue = ArgumentCaptor.forClass(IssueOtpCommand.class);
    verify(service).issue(issue.capture());
    assertEquals(customer, issue.getValue().customerId());
    var verification = ArgumentCaptor.forClass(OtpVerificationCommand.class);
    verify(service).verify(verification.capture());
    assertEquals(id, verification.getValue().challengeId());
    assertEquals(customer, verification.getValue().customerId());
  }

  @Test
  void rejectsMalformedIdsBeforeCallingApplication() {
    var service = mock(OtpApplicationService.class);
    var controller = new OtpController(service);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.verify(
                "invalid",
                new OtpController.VerifyOtpRequest(
                    "cus_ABCDEFGHIJKLMNOPQRSTUV", OtpPurpose.LOGIN, "123456", null),
                mock(HttpServletRequest.class)));
    verifyNoInteractions(service);
  }

  @Test
  void diagnosticOutputDoesNotExposeCodeButPayloadStillContainsIt() {
    var request =
        new OtpController.VerifyOtpRequest(
            "cus_ABCDEFGHIJKLMNOPQRSTUV", OtpPurpose.LOGIN, "123456", null);
    assertEquals("123456", request.code());
    assertFalse(request.toString().contains("123456"));
    assertFalse(
        new OtpVerificationCommand(
                OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV"),
                CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
                OtpPurpose.LOGIN,
                "123456",
                null,
                null)
            .toString()
            .contains("123456"));
    assertFalse(
        new VerifyOtpCommand(OtpPurpose.LOGIN, "123456", null, Instant.now(), false)
            .toString()
            .contains("123456"));
  }
}
