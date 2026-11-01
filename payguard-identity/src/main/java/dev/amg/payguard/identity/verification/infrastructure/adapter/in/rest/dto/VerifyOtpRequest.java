package dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
    @NotBlank String userId,
    @NotNull OtpPurpose purpose,
    @NotBlank @Pattern(regexp = "\\d{6}") String code,
    @NotBlank String deviceFingerprint) {

  @Override
  public String toString() {
    return "VerifyOtpRequest[purpose="
        + purpose
        + ", userId=[REDACTED]"
        + ", code=[REDACTED]"
        + ", deviceFingerprint=[REDACTED]]";
  }
}
