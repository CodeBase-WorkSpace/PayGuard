package dev.amg.payguard.identity.verification.domain.event;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;

/** Completion result, not a bearer credential or authorization for a financial operation. */
public record OtpVerificationEvent(
    OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant verifiedAt, Instant occurredOn)
    implements DomainEvent {
  public OtpVerificationEvent {
    Objects.requireNonNull(otpId, "OTP Id cannot be null");
    Objects.requireNonNull(customerId, "Customer id cannot be null");
    Objects.requireNonNull(purpose, "Purpose cannot be null");
    Objects.requireNonNull(verifiedAt, "Verification time cannot be null");
    Objects.requireNonNull(occurredOn, "Verification time cannot be null");
  }

  public static OtpVerificationEvent of(
      OtpId otpId, CustomerId customerId, OtpPurpose purpose, Instant verifiedAt) {
    return new OtpVerificationEvent(otpId, customerId, purpose, verifiedAt, Instant.now());
  }
}
