package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validates syntax, not existence or authorization. */
public final class OtpId implements ValueObject {
  private static final String OTP_ID_PREFIX = "otp_";
  private final String identifier;

  private OtpId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, OTP_ID_PREFIX);
  }

  public static OtpId parse(String value) {
    return new OtpId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || (other instanceof OtpId that && identifier.equals(that.identifier));
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(identifier);
  }

  @Override
  public String toString() {
    return identifier;
  }

  @Override
  public boolean sameValueAs(@Nullable ValueObject otherOtpId) {
    return equals(otherOtpId);
  }
}
