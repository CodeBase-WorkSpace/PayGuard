package dev.amg.payguard.identity.verification.infrastructure.delivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import org.junit.jupiter.api.Test;

class DeliveryAdaptersTest {
  @Test
  void mockRemainsLocalAndUnconfiguredProviderModesFailExplicitly() {
    var request =
        new OtpDeliveryRequest(
            OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV"),
            CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
            OtpPurpose.LOGIN,
            OtpChannel.SMS,
            "phone-ref",
            "123456");
    assertDoesNotThrow(() -> new MockDeliveryAdapter().submit(request));
    assertThrows(
        UnsupportedOperationException.class, () -> new SmsDeliveryAdapter().submit(request));
    assertThrows(
        UnsupportedOperationException.class, () -> new EmailDeliveryAdapter().submit(request));
    assertThrows(
        UnsupportedOperationException.class, () -> new PushDeliveryAdapter().submit(request));
  }
}
