package dev.amg.payguard.identity.verification.domain.event;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/** Raised when an active OTP challenge is invalidated. */
public record OtpInvalidatedEvent(
    OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant occurredOn)
    implements DomainEvent {

  public OtpInvalidatedEvent {
    Objects.requireNonNull(otpId, "challengeId must not be null");
    Objects.requireNonNull(customerId, "customerId must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
  }

  public static OtpInvalidatedEvent of(
      OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant occurredOn) {
    return new OtpInvalidatedEvent(otpId, customerId, purpose, occurredOn);
  }
}
