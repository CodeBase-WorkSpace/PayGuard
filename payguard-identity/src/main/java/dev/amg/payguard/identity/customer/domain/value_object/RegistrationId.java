package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validateAndReturns syntax, not existence or authorization. */
public final class RegistrationId implements ValueObject {
  private static final String REGISTER_ID_PREFIX = "reg_";
  private final String identifier;

  private RegistrationId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, REGISTER_ID_PREFIX);
  }

  public static RegistrationId parse(String value) {
    return new RegistrationId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other
        || (other instanceof RegistrationId that && identifier.equals(that.identifier));
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
  public boolean sameValueAs(@Nullable ValueObject otherRegisterId) {
    return equals(otherRegisterId);
  }
}
