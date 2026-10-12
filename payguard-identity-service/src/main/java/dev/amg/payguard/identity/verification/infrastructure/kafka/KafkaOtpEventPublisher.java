package dev.amg.payguard.identity.verification.infrastructure.kafka;

import dev.amg.payguard.identity.verification.domain.event.OtpEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class KafkaOtpEventPublisher implements OtpEventPublisher {

  private final KafkaTemplate<String, String> kafka;
  private final ObjectMapper objectMapper;
  private final String topic;

  public KafkaOtpEventPublisher(
      KafkaTemplate<String, String> kafka,
      ObjectMapper objectMapper,
      @Value("${identity.kafka.topic:identity.events.v1}") String topic) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.topic = topic;
  }

  @Override
  public void publish(OtpEvent event) {
    try {
      kafka.send(
          topic,
          event.challengeId().toString(),
          objectMapper.writeValueAsString(
              new EventPayload(
                  event.type(),
                  event.challengeId().value(),
                  event.customerId().getValue(),
                  event.purpose().name(),
                  event.channel().name(),
                  event.result() == null ? null : event.result().name(),
                  event.occurredAt(),
                  event.reason())));
    } catch (tools.jackson.core.JacksonException exception) {
      throw new IllegalStateException("Unable to serialize OTP event", exception);
    }
  }

  private record EventPayload(
      String type,
      String challengeId,
      String userId,
      String purpose,
      String channel,
      String result,
      java.time.Instant occurredAt,
      String reason) {}
}
