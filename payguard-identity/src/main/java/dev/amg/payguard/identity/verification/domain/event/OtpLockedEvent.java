package dev.amg.payguard.identity.verification.domain.event;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/** Raised when an OTP challenge is locked after exhausting its verification attempts. */
public record OtpLockedEvent(
    OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant occurredOn)
    implements DomainEvent {

  public OtpLockedEvent {
    Objects.requireNonNull(otpId, "challengeId must not be null");
    Objects.requireNonNull(customerId, "customerId must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(occurredOn, "occurredOn must not be null");
  }

  public static OtpLockedEvent of(
      OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant occurredOn) {
    return new OtpLockedEvent(otpId, customerId, purpose, occurredOn);
  }
}
