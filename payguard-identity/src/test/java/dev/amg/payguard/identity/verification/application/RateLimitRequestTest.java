package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitOperation;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import org.junit.jupiter.api.Test;

class RateLimitRequestTest {

  @Test
  void shouldCreateRequestWithCustomerId() {
    RateLimitRequest request =
        new RateLimitRequest(
            RateLimitOperation.ISSUE, "customer-123", null, "192.0.2.10", OtpPurpose.LOGIN);

    assertEquals(RateLimitOperation.ISSUE, request.operation());
    assertEquals("customer-123", request.customerId());
    assertNull(request.recipientFingerprint());
    assertEquals("192.0.2.10", request.ipAddress());
  }

  @Test
  void shouldCreateRequestWithRecipientFingerprint() {
    RateLimitRequest request =
        new RateLimitRequest(
            RateLimitOperation.VERIFY,
            null,
            "recipient-fingerprint",
            "192.0.2.10",
            OtpPurpose.LOGIN);

    assertEquals("recipient-fingerprint", request.recipientFingerprint());
  }

  @Test
  void shouldAcceptBothIdentityDimensions() {
    RateLimitRequest request =
        new RateLimitRequest(
            RateLimitOperation.ISSUE,
            "customer-123",
            "recipient-fingerprint",
            "192.0.2.10",
            OtpPurpose.LOGIN);

    assertNotNull(request.customerId());
    assertNotNull(request.recipientFingerprint());
  }

  @Test
  void shouldRejectMissingOperation() {
    assertThrows(
        NullPointerException.class,
        () -> new RateLimitRequest(null, "customer-123", null, "192.0.2.10", OtpPurpose.LOGIN));
  }

  @Test
  void shouldRejectMissingPurpose() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, "customer-123", null, "192.0.2.10", null));
  }

  @Test
  void shouldRejectMissingIpAddress() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, "customer-123", null, null, OtpPurpose.LOGIN));
  }

  @Test
  void shouldRejectBlankIpAddress() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, "customer-123", null, "   ", OtpPurpose.LOGIN));
  }

  @Test
  void shouldRejectBlankCustomerId() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, " ", null, "192.0.2.10", OtpPurpose.LOGIN));
  }

  @Test
  void shouldRejectBlankRecipientFingerprint() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, null, "", "192.0.2.10", OtpPurpose.LOGIN));
  }

  @Test
  void shouldRejectRequestWithoutIdentity() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RateLimitRequest(
                RateLimitOperation.ISSUE, null, null, "192.0.2.10", OtpPurpose.LOGIN));
  }

  @Test
  void shouldPreserveRecordEquality() {
    RateLimitRequest first =
        new RateLimitRequest(
            RateLimitOperation.ISSUE, "customer-123", null, "192.0.2.10", OtpPurpose.LOGIN);

    RateLimitRequest second =
        new RateLimitRequest(
            RateLimitOperation.ISSUE, "customer-123", null, "192.0.2.10", OtpPurpose.LOGIN);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}
