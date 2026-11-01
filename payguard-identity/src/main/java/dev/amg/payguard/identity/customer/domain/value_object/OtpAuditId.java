package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validateAndReturns syntax, not existence or authorization. */
public final class OtpAuditId implements ValueObject {
  private static final String AUDIT_ID_PREFIX = "oaud_";
  private final String identifier;

  private OtpAuditId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, AUDIT_ID_PREFIX);
  }

  public static OtpAuditId parse(String value) {
    return new OtpAuditId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || (other instanceof OtpAuditId that && identifier.equals(that.identifier));
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
  public boolean sameValueAs(@Nullable ValueObject otherAuditId) {
    return equals(otherAuditId);
  }
}
