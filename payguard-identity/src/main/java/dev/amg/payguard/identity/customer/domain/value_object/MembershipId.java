package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validateAndReturns syntax, not existence or authorization. */
public final class MembershipId implements ValueObject {
  private static final String MEMBERSHIP_ID_PREFIX = "mem_";
  private final String identifier;

  private MembershipId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, MEMBERSHIP_ID_PREFIX);
  }

  public static MembershipId parse(String value) {
    return new MembershipId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || (other instanceof MembershipId that && identifier.equals(that.identifier));
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
  public boolean sameValueAs(@Nullable ValueObject otherMembershipId) {
    return equals(otherMembershipId);
  }
}
