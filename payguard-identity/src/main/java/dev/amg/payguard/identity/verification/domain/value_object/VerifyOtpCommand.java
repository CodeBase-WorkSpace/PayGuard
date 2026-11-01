package dev.amg.payguard.identity.verification.domain.value_object;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import domain.ValueObject;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record VerifyOtpCommand(
    OtpPurpose purpose, String code, @Nullable String deviceFingerprint, Instant now)
    implements ValueObject {

  public VerifyOtpCommand {
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(now, "now must not be null");
  }

  // other represents another whole value object. Comparing it with deviceFingerprint cannot
  // validate constructor arguments.
  // Generated equals compares every component, handles nullable fingerprints, and returns false for
  // null.
  @Override
  public boolean sameValueAs(@Nullable ValueObject other) {
    return other instanceof VerifyOtpCommand;
  }
}
