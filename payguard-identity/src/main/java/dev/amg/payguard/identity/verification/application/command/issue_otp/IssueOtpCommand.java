package dev.amg.payguard.identity.verification.application.command.issue_otp;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Command requesting issuance of a new OTP challenge. */
@NullMarked
public record IssueOtpCommand(
    CustomerId customerId,
    OtpPurpose purpose,
    OtpChannel channel,
    String destinationRef,
    @Nullable String deviceFingerprint,
    String sourceIp) {
  public IssueOtpCommand {
    Objects.requireNonNull(customerId, "CustomerId cannot be null");
    Objects.requireNonNull(purpose, "Purpose cannot be null");
    Objects.requireNonNull(channel, "Channel cannot be null");
    Objects.requireNonNull(destinationRef, "Destination Reference cannot be null");
    Objects.requireNonNull(sourceIp, "Source IP cannot be null");

    if (destinationRef.isBlank() || sourceIp.isBlank()) {
      throw new UnsupportedOperationException();
    }
    if (deviceFingerprint != null && deviceFingerprint.isBlank()) {
      throw new UnsupportedOperationException();
    }
  }

  public static IssueOtpCommand of(
      CustomerId customerId,
      OtpPurpose purpose,
      OtpChannel channel,
      String destinationRef,
      String deviceFingerprint,
      String sourceIp) {
    return new IssueOtpCommand(
        customerId, purpose, channel, destinationRef, deviceFingerprint, sourceIp);
  }

  @Override
  public String toString() {
    return "IssueOtpCommand[purpose="
        + purpose
        + ", channel="
        + channel
        + ", destinationRef=[REDACTED], deviceFingerprint=[REDACTED], sourceIp=[REDACTED]]";
  }
}
