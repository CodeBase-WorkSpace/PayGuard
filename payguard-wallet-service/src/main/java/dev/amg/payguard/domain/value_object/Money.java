package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.CurrencyMismatchException;
import dev.amg.payguard.domain.exception.NegativeAmountNotAllowedException;
import domain.DomainValidation;
import domain.ValueObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal amount, Currency currency)
    implements Comparable<Money>, ValueObject {

  public Money {
    DomainValidation.requireNonNull(amount, "Amount");
    DomainValidation.requireNonNull(currency, "Currency");

    int fractionDigits = Math.max(currency.getDefaultFractionDigits(), 0);
    amount = amount.setScale(fractionDigits, RoundingMode.HALF_EVEN);
  }

  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  public static Money of(String amount, Currency currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money of(long amount, Currency currency) {
    return new Money(BigDecimal.valueOf(amount), currency);
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money add(Money other) {
    assertSameCurrency(other);
    return new Money(this.amount.add(other.amount), this.currency);
  }

  public Money subtract(Money other) {
    assertSameCurrency(other);
    return new Money(this.amount.subtract(other.amount), this.currency);
  }

  public Money multiply(long factor) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
  }

  public Money multiply(BigDecimal factor) {
    DomainValidation.requireNonNull(factor, "Multiplication factor");
    return new Money(this.amount.multiply(factor), this.currency);
  }

  public Money abs() {
    return new Money(this.amount.abs(), this.currency);
  }

  public Money negate() {
    return new Money(this.amount.negate(), this.currency);
  }

  public void assertNonNegative() {
    if (isNegative()) {
      throw new NegativeAmountNotAllowedException("Amount cannot be negative: " + this.amount);
    }
  }

  public boolean isPositive() {
    return this.amount.compareTo(BigDecimal.ZERO) > 0;
  }

  public boolean isNegative() {
    return this.amount.compareTo(BigDecimal.ZERO) < 0;
  }

  public boolean isZero() {
    return this.amount.compareTo(BigDecimal.ZERO) == 0;
  }

  public boolean isGreaterThan(Money other) {
    assertSameCurrency(other);
    return this.compareTo(other) > 0;
  }

  public boolean isGreaterThanOrEqual(Money other) {
    assertSameCurrency(other);
    return this.compareTo(other) >= 0;
  }

  public boolean isLessThan(Money other) {
    assertSameCurrency(other);
    return this.compareTo(other) < 0;
  }

  public boolean isLessThanOrEqual(Money other) {
    assertSameCurrency(other);
    return this.compareTo(other) <= 0;
  }

  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Money money)) {
      return false;
    }
    return amount.equals(money.amount) && currency.equals(money.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, currency);
  }

  private void assertSameCurrency(Money other) {
    DomainValidation.requireNonNull(other, "Comparing Money operand");
    if (!this.currency.equals(other.currency)) {
      throw new CurrencyMismatchException(
          String.format(
              "Currencies do not match: %s vs %s",
              this.currency.getCurrencyCode(), other.currency.getCurrencyCode()));
    }
  }

  @Override
  public int compareTo(Money o) {
    assertSameCurrency(o);
    return this.amount.compareTo(o.amount);
  }
}
