package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.CurrencyMismatchException;
import dev.amg.payguard.domain.exception.NegativeAmountNotAllowedException;
import domain.DomainValidation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import domain.ValueObject;

/**
 * Exact, immutable monetary amount with its currency.
 *
 * <p>Values are normalized to the currency's default ISO 4217 minor-unit scale;
 * no implicit rounding is allowed. Negative values are valid; operation-specific
 * sign constraints belong to the Wallet domain rules.
 *
 * <p>Comparison is supported only for equal currencies.
 *
 * @param amount exact value in major currency units
 * @param currency ISO 4217 currency
 */
public record Money(BigDecimal amount, Currency currency)
    implements Comparable<Money>, ValueObject {

  /** Validates currency metadata and exact monetary scale. */
  public Money {
    DomainValidation.requireNonNull(amount, "Amount");
    DomainValidation.requireNonNull(currency, "Currency");
    int fractionDigits = currency.getDefaultFractionDigits();
    if (fractionDigits < 0) {
      throw new IllegalArgumentException(
          "Unsupported currency precision: " + currency.getCurrencyCode());
    }
    amount = amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
  }

  /** Constructs Money from an exact decimal value. */
  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  /** Parses an exact base-10 decimal representation. */
  public static Money of(String amount, Currency currency) {
    DomainValidation.requireNonNull(amount, "Amount string");
    return new Money(new BigDecimal(amount), currency);
  }

  /** Constructs Money from whole major currency units (not minor units). */
  public static Money of(long amount, Currency currency) {
    return new Money(BigDecimal.valueOf(amount), currency);
  }

  /** Creates an exact zero using the currency's configured scale. */
  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  /** Adds compatible amounts without rounding. */
  public Money add(Money other) {
    assertSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  /** Subtracts compatible amounts without rounding. */
  public Money subtract(Money other) {
    assertSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  /** Multiplies the amount by an integer factor, preserving exact precision. */
  public Money multiply(long factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }

  /** Multiplies by a decimal factor; rejects results that require rounding. */
  public Money multiply(BigDecimal factor) {
    DomainValidation.requireNonNull(factor, "Multiplication factor");
    return new Money(amount.multiply(factor), currency);
  }

  /** Returns the absolute value in the same currency. */
  public Money abs() {
    return new Money(amount.abs(), currency);
  }

  /** Returns the additive inverse in the same currency. */
  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  /** Asserts that the monetary value is zero or positive. */
  public void assertNonNegative() {
    if (isNegative()) {
      throw new NegativeAmountNotAllowedException(
          "Amount cannot be negative: " + amount);
    }
  }

  /** Tests whether the amount is strictly positive. */
  public boolean isPositive() {
    return amount.signum() > 0;
  }

  /** Tests whether the amount is strictly negative. */
  public boolean isNegative() {
    return amount.signum() < 0;
  }

  /** Tests whether the amount is zero. */
  public boolean isZero() {
    return amount.signum() == 0;
  }

  /** Compares compatible amounts for a strictly greater value. */
  public boolean isGreaterThan(Money other) {
    return compareTo(other) > 0;
  }

  /** Compares compatible amounts for a greater-or-equal value. */
  public boolean isGreaterThanOrEqual(Money other) {
    return compareTo(other) >= 0;
  }

  /** Compares compatible amounts for a strictly smaller value. */
  public boolean isLessThan(Money other) {
    return compareTo(other) < 0;
  }

  /** Compares compatible amounts for a smaller-or-equal value. */
  public boolean isLessThanOrEqual(Money other) {
    return compareTo(other) <= 0;
  }

  /**
   * Orders monetary values in the same currency only.
   *
   * @throws CurrencyMismatchException for different currencies
   */
  @Override
  public int compareTo(Money other) {
    assertSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  /** Compares value objects using exact normalized record equality. */
  @Override
  public boolean sameValueAs(ValueObject other) {
    return equals(other);
  }

  private void assertSameCurrency(Money other) {
    DomainValidation.requireNonNull(other, "Money operand");
    if (!currency.equals(other.currency)) {
      throw new CurrencyMismatchException(
          "Currencies do not match: %s vs %s"
              .formatted(currency.getCurrencyCode(), other.currency.getCurrencyCode()));
    }
  }
}
