package dev.amg.payguard.identity.verification.application.command.otp_verification;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.command.otp_verification.exception.InvalidOtpVerificationCommandException;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record OtpVerificationCommand(
    OtpId otpId,
    CustomerId customerId,
    OtpPurpose purpose,
    String code,
    String deviceFingerprint,
    String sourceIp) {

  public OtpVerificationCommand {
    requirePresent(otpId, "otpId");
    requirePresent(customerId, "customerId");
    requirePresent(purpose, "purpose");

    requireNotBlank(code, "code");
    requireNotBlank(deviceFingerprint, "deviceFingerprint");
    requireNotBlank(sourceIp, "sourceIp");

    if (code.isBlank()) {
      throw new InvalidOtpVerificationCommandException("OTP code must not be blank");
    }

    if (deviceFingerprint.isBlank()) {
      throw new InvalidOtpVerificationCommandException("Device fingerprint must not be blank");
    }

    if (sourceIp.isBlank()) {
      throw new InvalidOtpVerificationCommandException("Source IP must not be blank");
    }
  }

  public static OtpVerificationCommand of(
      OtpId challengeId,
      CustomerId customerId,
      OtpPurpose purpose,
      String code,
      String deviceFingerprint,
      String sourceIp) {

    return new OtpVerificationCommand(
        challengeId, customerId, purpose, code, deviceFingerprint, sourceIp);
  }

  @Override
  public String toString() {
    return "OtpVerificationCommand[otpId="
        + otpId
        + ", code=[REDACTED], deviceFingerprint=[REDACTED], sourceIp=[REDACTED]]";
  }

  private static void requireNotBlank(@Nullable String value, String field) {
    if (value == null || value.isBlank()) {
      throw new InvalidOtpVerificationCommandException(field + " must not be null or blank");
    }
  }

  private static void requirePresent(@Nullable Object value, String field) {
    if (value == null) {
      throw new InvalidOtpVerificationCommandException(field + " must not be null");
    }
  }
}
