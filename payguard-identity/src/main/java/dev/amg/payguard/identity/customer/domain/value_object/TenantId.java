package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Immutable identifier. Parsing validateAndReturns syntax, not existence or authorization. */
public final class TenantId implements ValueObject {
  private static final String TENANT_ID_PREFIX = "ten_";
  private final String identifier;

  private TenantId(String value) {
    this.identifier = PrefixedIdValidator.validateAndReturn(value, TENANT_ID_PREFIX);
  }

  public static TenantId parse(String value) {
    return new TenantId(value);
  }

  public String value() {
    return identifier;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return this == other || (other instanceof TenantId that && identifier.equals(that.identifier));
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
  public boolean sameValueAs(@Nullable ValueObject otherTenantId) {
    return equals(otherTenantId);
  }
}
