package dev.amg.payguard.identity.verification.domain.event;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import domain.DomainEvent;
import java.time.Instant;
import java.util.Objects;

public record OtpIssuedEvent(
    OtpId otpId,
    CustomerId customerId,
    OtpPurpose purpose,
    OtpChannel channel,
    Instant expiresAt,
    Instant occurredOn)
    implements DomainEvent {
  public OtpIssuedEvent {
    Objects.requireNonNull(otpId, "OTP Id cannot be null");
    Objects.requireNonNull(customerId, "Customer id cannot be null");
    Objects.requireNonNull(purpose, "Purpose cannot be null");
    Objects.requireNonNull(channel, "Channel cannot be null");
    Objects.requireNonNull(expiresAt, "Expires at cannot be null");
    Objects.requireNonNull(occurredOn, "Occurred on cannot be null");
  }

  public static OtpIssuedEvent of(
      OtpId otpId,
      CustomerId customerId,
      OtpPurpose purpose,
      OtpChannel channel,
      Instant expiresAt) {
    return new OtpIssuedEvent(otpId, customerId, purpose, channel, expiresAt, Instant.now());
  }
}
