package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import org.jspecify.annotations.NullMarked;

/** destinationRef is the caller's delivery reference; provider resolution is not implemented. */
@NullMarked
public record OtpDeliveryRequest(
    OtpId otpId,
    CustomerId customerId,
    OtpPurpose purpose,
    OtpChannel channel,
    String destinationRef,
    String code) {
  @Override
  public String toString() {
    return "OtpDeliveryRequest[otpId=" + otpId + ", code=[REDACTED]]";
  }
}
