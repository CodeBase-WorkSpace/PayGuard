package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validates syntax, not existence or authorization. */
public final class AccountId implements ValueObject {
  private static final String ACCOUNT_ID_PREFIX = "acc_";
  private final String identifier;

  private AccountId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, ACCOUNT_ID_PREFIX);
  }

  public static AccountId parse(String value) {
    return new AccountId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || (other instanceof AccountId that && identifier.equals(that.identifier));
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
  public boolean sameValueAs(@Nullable ValueObject otherAccountId) {
    return equals(otherAccountId);
  }
}
