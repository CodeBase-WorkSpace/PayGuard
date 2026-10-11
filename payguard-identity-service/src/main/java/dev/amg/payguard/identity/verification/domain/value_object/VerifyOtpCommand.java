package dev.amg.payguard.identity.verification.domain.value_object;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record VerifyOtpCommand(
    OtpPurpose purpose,
    String code,
    @Nullable String deviceFingerprint,
    Instant now,
    boolean authenticatorCodeValid // Used when verificationMode == TOTP
    ) {
  @Override
  public String toString() {
    return "VerifyOTPCommand[purpose=" + purpose + ", code=[REDACTED]]";
  }
}
