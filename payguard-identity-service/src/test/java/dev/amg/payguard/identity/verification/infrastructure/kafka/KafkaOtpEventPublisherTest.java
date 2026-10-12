package dev.amg.payguard.identity.verification.infrastructure.kafka;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.event.OtpEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

class KafkaOtpEventPublisherTest {
  @Test
  @SuppressWarnings("unchecked")
  void convertsTypedIdsToExistingWireFields() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    OtpId id = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
    var publisher = new KafkaOtpEventPublisher(kafka, new ObjectMapper(), "identity.events.v1");
    publisher.publish(
        new OtpEvent(
            "identity.verified",
            id,
            CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV"),
            OtpPurpose.LOGIN,
            OtpChannel.SMS,
            OtpVerificationResult.VERIFIED,
            Instant.parse("2026-01-01T00:00:00Z"),
            "verified"));
    var json = ArgumentCaptor.forClass(String.class);
    verify(kafka).send(eq("identity.events.v1"), eq(id.value()), json.capture());
    assertTrue(json.getValue().contains("\"challengeId\":\"otp_ABCDEFGHIJKLMNOPQRSTUV\""));
    assertTrue(json.getValue().contains("\"userId\":\"cus_ABCDEFGHIJKLMNOPQRSTUV\""));
    assertFalse(json.getValue().contains("customerId"));
  }
}
