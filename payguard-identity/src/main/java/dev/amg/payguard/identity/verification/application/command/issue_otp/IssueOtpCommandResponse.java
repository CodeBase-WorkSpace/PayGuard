package dev.amg.payguard.identity.verification.application.command.issue_otp;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;
import java.util.Objects;

/** Metadata returned after an OTP challenge has been issued. */
public record IssueOtpCommandResponse(
    OtpId otpId,
    OtpPurpose purpose,
    OtpChannel channel,
    String destinationRef,
    String sourceIp,
    Instant expiresAt) {
  public IssueOtpCommandResponse {
    Objects.requireNonNull(otpId, "challengeId must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(channel, "channel must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  public static IssueOtpCommandResponse from(OtpId id, IssueOtpCommand command, Instant expiresAt) {
    return new IssueOtpCommandResponse(
        id,
        command.purpose(),
        command.channel(),
        command.destinationRef(),
        command.sourceIp(),
        expiresAt);
  }
}
