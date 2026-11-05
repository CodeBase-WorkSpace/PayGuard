package dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq;

import dev.amg.payguard.identity.verification.domain.event.OtpExpiredEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpInvalidatedEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpIssuedEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpLockedEvent;
import dev.amg.payguard.identity.verification.domain.event.OtpVerificationEvent;
import domain.DomainEvent;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public final class JsonOtpOutboxEncoder implements OtpOutboxEncoder {
  private static final String OTP_ID = "challengeId";
  private static final String CUSTOMER_ID = "customerId";
  private static final String PURPOSE = "purpose";
  private static final String OCCURRED_ON = "occurredOn";
  private final ObjectMapper mapper;

  public JsonOtpOutboxEncoder(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public EncodedEvent encode(DomainEvent event) {
    Map<String, String> payload =
        switch (event) {
          case OtpIssuedEvent issued ->
              Map.of(
                  OTP_ID,
                  issued.otpId().value(),
                  CUSTOMER_ID,
                  issued.customerId().getValue(),
                  PURPOSE,
                  issued.purpose().name(),
                  "channel",
                  issued.channel().name(),
                  "expiresAt",
                  issued.expiresAt().toString(),
                  OCCURRED_ON,
                  issued.occurredOn().toString());
          case OtpVerificationEvent verified ->
              Map.of(
                  OTP_ID,
                  verified.otpId().value(),
                  CUSTOMER_ID,
                  verified.customerId().getValue(),
                  PURPOSE,
                  verified.purpose().name(),
                  "verifiedAt",
                  verified.verifiedAt().toString(),
                  OCCURRED_ON,
                  verified.occurredOn().toString());
          case OtpExpiredEvent expired ->
              Map.of(
                  OTP_ID, expired.otpId().value(),
                  CUSTOMER_ID, expired.customerId().getValue(),
                  PURPOSE, expired.purpose().name(),
                  OCCURRED_ON, expired.occurredOn().toString());
          case OtpLockedEvent locked ->
              Map.of(
                  OTP_ID, locked.otpId().value(),
                  CUSTOMER_ID, locked.customerId().getValue(),
                  PURPOSE, locked.purpose().name(),
                  OCCURRED_ON, locked.occurredOn().toString());
          case OtpInvalidatedEvent invalidated ->
              Map.of(
                  OTP_ID, invalidated.otpId().value(),
                  CUSTOMER_ID, invalidated.customerId().getValue(),
                  PURPOSE, invalidated.purpose().name(),
                  OCCURRED_ON, invalidated.occurredOn().toString());
          default ->
              throw new IllegalArgumentException("Unsupported OTP event: " + event.getClass());
        };
    try {
      return new EncodedEvent(event.getClass().getSimpleName(), mapper.writeValueAsString(payload));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to encode OTP outbox event", exception);
    }
  }
}
