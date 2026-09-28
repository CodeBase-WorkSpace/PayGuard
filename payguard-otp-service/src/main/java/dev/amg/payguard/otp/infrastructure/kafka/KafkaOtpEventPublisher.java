package dev.amg.payguard.otp.infrastructure.kafka;

import dev.amg.payguard.otp.domain.OtpEventPublisher;
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
      @Value("${otp.kafka.topic:otp.events.v1}") String topic) {
    this.kafka = kafka;
    this.objectMapper = objectMapper;
    this.topic = topic;
  }

  @Override
  public void publish(OtpEvent event) {
    try {
      kafka.send(topic, event.challengeId().toString(), objectMapper.writeValueAsString(event));
    } catch (tools.jackson.core.JacksonException exception) {
      throw new IllegalStateException("Unable to serialize OTP event", exception);
    }
  }
}
