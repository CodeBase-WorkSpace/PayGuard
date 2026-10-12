package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;

public record OtpVerificationCommand(
    OtpId challengeId,
    CustomerId customerId,
    OtpPurpose purpose,
    String code,
    String deviceFingerprint,
    String sourceIp) {
  @Override
  public String toString() {
    return "VerifyOtpCommand[challengeId=" + challengeId + ", code=[REDACTED]]";
  }
}
