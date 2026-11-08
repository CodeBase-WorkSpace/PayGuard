package dev.amg.payguard.identity.verification.infrastructure.adapter.out.kafka;

import dev.amg.payguard.identity.verification.domain.event.OtpEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.kafka.contracts.OtpEventV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

@Component
public class KafkaOtpEventPublisher implements OtpEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOtpEventPublisher.class);

    // Dedicated mapper: the wire format no longer depends on the app-wide ObjectMapper config.
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaOtpEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
      @Value("${identity.kafka.topic:identity.events.v1}") String topic) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.topic = Objects.requireNonNull(topic, "topic");
  }

  @Override
  public void publish(OtpEvent event) {
      Objects.requireNonNull(event);

      OtpEventV1 payload = toPayload(event);
      final String json = serialize(payload);

      // Key = customerId: all events of one customer land in one partition, so they stay ordered.
      kafkaTemplate
              .send(topic, payload.customerId(), json)
              .whenComplete((result, ex) -> {
                          if (ex != null) {
                              log.error("Failed to publish OTP event eventId={} challengeId={} topic={}",
                                      payload.eventId(),
                                      payload.challengeId(),
                                      topic,
                                      ex
                              );
                          } else if (log.isDebugEnabled()) {
                              log.debug(
                                      "Published OTP event eventId={} partition={} offset={}",
                                      payload.eventId(),
                                      result.getRecordMetadata().partition(),
                                      result.getRecordMetadata().offset());
                          }
                      }
              );

  }


    private OtpEventV1 toPayload(OtpEvent event) {
        final String challengeId = event.challengeId().value();
        final String customerId = event.customerId().getValue();
        final String result = event.result() == null ? null : event.result().name();

        return new OtpEventV1(
                event.id().toString(),
                OtpEventV1.SCHEMA_VERSION,
                event.type(),
                challengeId,
                customerId,
                event.purpose().name(),
                event.channel().name(),
                result,
                event.occurredAt().toString(),
                event.reason()
        );
    }

    private String serialize(OtpEventV1 payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JacksonException exception) {
            // Deterministic bug (not a transient error): fail fast, a retry would never help.
            throw new IllegalStateException("Unable to serialize OTP event " + payload.eventId(), exception);
        }
    }

}
