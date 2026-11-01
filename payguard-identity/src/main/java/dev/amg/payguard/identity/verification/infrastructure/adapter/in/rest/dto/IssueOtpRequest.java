package dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto;

import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IssueOtpRequest(
    @NotBlank String userId,
    @NotNull OtpPurpose purpose,
    @NotNull OtpChannel channel,
    @NotBlank String destinationRef,
    @Nullable String deviceFingerprint) {

  @Override
  public String toString() {
    return "IssueOtpRequest[purpose="
        + purpose
        + ", channel="
        + channel
        + ", userId=[REDACTED]"
        + ", destinationRef=[REDACTED]"
        + ", deviceFingerprint=[REDACTED]]";
  }
}
