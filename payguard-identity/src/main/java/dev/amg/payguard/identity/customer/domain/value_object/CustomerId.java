package dev.amg.payguard.identity.customer.domain.value_object;

import domain.ValueObject;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.NamedInterface;

@NamedInterface
public final class CustomerId implements ValueObject {
  private static final String CUSTOMER_ID_PREFIX = "cus_";
  private final String value;

  private CustomerId(String value) {
    this.value = PrefixedIdValidator.validateAndReturn(value, CUSTOMER_ID_PREFIX);
  }

  public static CustomerId parse(String value) {
    return new CustomerId(value);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (!(o instanceof CustomerId that)) return false;

    return getValue().equals(that.getValue());
  }

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean sameValueAs(@Nullable ValueObject otherCustomerId) {
    return equals(otherCustomerId);
  }
}
