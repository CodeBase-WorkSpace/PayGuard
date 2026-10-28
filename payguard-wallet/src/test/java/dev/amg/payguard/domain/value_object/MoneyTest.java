package dev.amg.payguard.domain.value_object;

import dev.amg.payguard.domain.exception.CurrencyMismatchException;
import dev.amg.payguard.domain.exception.NegativeAmountNotAllowedException;
import domain.InvalidDomainArgumentException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests exact financial arithmetic, normalization and currency isolation. */
class MoneyTest {

  private static final Currency CHF = Currency.getInstance("CHF");
  private static final Currency USD = Currency.getInstance("USD");

  /** A normal CHF amount keeps its exact amount and currency. */
  @Test
  void shouldAcceptExactChfAmount() {
    Money money = Money.of("100.25", CHF);
    assertEquals(new BigDecimal("100.25"), money.amount());
    assertEquals(CHF, money.currency());
  }

  /** A CHF amount with one decimal place normalizes to two. */
  @Test
  void shouldNormalizeChfFractionalPrecision() {
    assertEquals(new BigDecimal("100.50"), Money.of("100.5", CHF).amount());
  }

  /** Excess zero digits can be removed without rounding. */
  @Test
  void shouldAcceptRedundantTrailingZeros() {
    assertEquals(new BigDecimal("100.50"), Money.of("100.5000", CHF).amount());
  }

  /** A CHF amount needing rounding is rejected rather than adjusted. */
  @Test
  void shouldRejectInexactChfFractionalPrecision() {
    assertThrows(ArithmeticException.class, () -> Money.of("100.555", CHF));
  }

  /** Even a tiny third decimal digit must not be silently rounded for CHF. */
  @Test
  void shouldRejectSubCentChfAmount() {
    assertThrows(ArithmeticException.class, () -> Money.of("0.001", CHF));
  }

  /** Zero-currency placeholder XXX has no defined minor-unit precision. */
  @Test
  void shouldRejectUndefinedCurrencyPrecision() {
    assertThrows(IllegalArgumentException.class,
        () -> Money.of("100", Currency.getInstance("XXX")));
  }

  /** Zero is normalized to the chosen currency scale. */
  @Test
  void shouldCreateZeroWithNormalizedScale() {
    Money zero = Money.zero(CHF);
    assertEquals(new BigDecimal("0.00"), zero.amount());
    assertTrue(zero.isZero());
    assertFalse(zero.isPositive());
    assertFalse(zero.isNegative());
  }

  /** Positive values have only the positive predicate set. */
  @Test
  void shouldIdentifyPositiveMoney() {
    Money value = Money.of("1.00", CHF);
    assertTrue(value.isPositive());
    assertFalse(value.isNegative());
    assertFalse(value.isZero());
  }

  /** Negative values remain representable at value-object level. */
  @Test
  void shouldIdentifyNegativeMoney() {
    Money value = Money.of("-1.00", CHF);
    assertTrue(value.isNegative());
    assertFalse(value.isPositive());
    assertFalse(value.isZero());
  }

  /** Adding amounts leaves the two operands unchanged. */
  @Test
  void shouldAddExactAmountsWithoutMutation() {
    Money first = Money.of("100.25", CHF);
    Money second = Money.of("0.35", CHF);
    assertEquals(Money.of("100.60", CHF), first.add(second));
    assertEquals(Money.of("100.25", CHF), first);
    assertEquals(Money.of("0.35", CHF), second);
  }

  /** Subtraction may produce a negative Money value. */
  @Test
  void shouldSubtractExactlyIntoNegativeBalance() {
    assertEquals(Money.of("-50.00", CHF),
        Money.of("100.00", CHF).subtract(Money.of("150.00", CHF)));
  }

  /** Mixed-currency addition is always rejected. */
  @Test
  void shouldRejectCrossCurrencyAddition() {
    assertThrows(CurrencyMismatchException.class,
        () -> Money.of("1.00", CHF).add(Money.of("1.00", USD)));
  }

  /** Mixed-currency subtraction is always rejected. */
  @Test
  void shouldRejectCrossCurrencySubtraction() {
    assertThrows(CurrencyMismatchException.class,
        () -> Money.of("1.00", CHF).subtract(Money.of("1.00", USD)));
  }

  /** Multiplication by a whole number is exact, including sign. */
  @Test
  void shouldMultiplyByWholeNumber() {
    assertEquals(Money.of("-30.75", CHF), Money.of("10.25", CHF).multiply(-3L));
  }

  /** Decimal multiplication succeeds when the result has valid precision. */
  @Test
  void shouldMultiplyByExactDecimalFactor() {
    assertEquals(Money.of("5.00", CHF),
        Money.of("10.00", CHF).multiply(new BigDecimal("0.5")));
  }

  /** Decimal multiplication fails rather than silently rounding half cents. */
  @Test
  void shouldRejectInexactDecimalMultiplication() {
    assertThrows(ArithmeticException.class,
        () -> Money.of("10.01", CHF).multiply(new BigDecimal("0.5")));
  }

  /** Zero multiplication preserves currency and normalized scale. */
  @Test
  void shouldMultiplyByZero() {
    assertEquals(Money.zero(CHF), Money.of("10.25", CHF).multiply(0L));
  }

  /** Negation returns the inverse without mutating the original value. */
  @Test
  void shouldNegateWithoutMutating() {
    Money original = Money.of("5.50", CHF);
    assertEquals(Money.of("-5.50", CHF), original.negate());
    assertEquals(Money.of("5.50", CHF), original);
  }

  /** Absolute value removes a negative sign. */
  @Test
  void shouldReturnAbsoluteValue() {
    assertEquals(Money.of("5.50", CHF), Money.of("-5.50", CHF).abs());
  }

  /** A negative amount is rejected where nonnegative is explicitly required. */
  @Test
  void shouldRejectNegativeAmountForNonNegativeInvariant() {
    assertThrows(NegativeAmountNotAllowedException.class,
        () -> Money.of("-0.01", CHF).assertNonNegative());
  }

  /** Zero and positive amounts satisfy the nonnegative invariant. */
  @Test
  void shouldAcceptNonNegativeAmounts() {
    assertDoesNotThrow(() -> Money.zero(CHF).assertNonNegative());
    assertDoesNotThrow(() -> Money.of("0.01", CHF).assertNonNegative());
  }

  /** Different legal textual representations of a value normalize equally. */
  @Test
  void shouldPreserveNormalizedEqualityAndHashCode() {
    Money first = Money.of("10.0", CHF);
    Money second = Money.of("10.00", CHF);
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertTrue(first.sameValueAs(second));
    assertFalse(first.sameValueAs(null));
  }

  /** Currency participates in value equality even when amounts are equal. */
  @Test
  void shouldIncludeCurrencyInEquality() {
    assertNotEquals(Money.of("1.00", CHF), Money.of("1.00", USD));
  }

  /** Same-currency ordering is supported for normal values. */
  @Test
  void shouldCompareSameCurrencyAmounts() {
    Money one = Money.of("1.00", CHF);
    Money two = Money.of("2.00", CHF);
    assertTrue(one.compareTo(two) < 0);
    assertTrue(two.compareTo(one) > 0);
    assertEquals(0, one.compareTo(Money.of("1.0", CHF)));
  }

  /** Convenience comparisons share the currency-safety invariant. */
  @Test
  void shouldEvaluateConvenienceComparisons() {
    Money one = Money.of("1.00", CHF);
    Money two = Money.of("2.00", CHF);
    assertTrue(two.isGreaterThan(one));
    assertTrue(two.isGreaterThanOrEqual(two));
    assertTrue(one.isLessThan(two));
    assertTrue(one.isLessThanOrEqual(one));
  }

  /** Ordering across currencies is not meaningful. */
  @Test
  void shouldRejectCrossCurrencyComparison() {
    assertThrows(CurrencyMismatchException.class,
        () -> Money.of("1.00", CHF).compareTo(Money.of("1.00", USD)));
  }

  /** Null operands are rejected before arithmetic or comparison. */
  @Test
  void shouldRejectNullOperands() {
    Money one = Money.of("1.00", CHF);
    assertThrows(InvalidDomainArgumentException.class, () -> one.add(null));
    assertThrows(InvalidDomainArgumentException.class, () -> one.subtract(null));
    assertThrows(InvalidDomainArgumentException.class, () -> one.compareTo(null));
  }

  /** Null amounts are rejected by the shared-domain validator. */
  @Test
  void shouldRejectNullAmount() {
    assertThrows(InvalidDomainArgumentException.class,
        () -> Money.of((BigDecimal) null, CHF));
  }

  /** Null currencies are rejected by the shared-domain validator. */
  @Test
  void shouldRejectNullCurrency() {
    assertThrows(InvalidDomainArgumentException.class, () -> Money.of("1", null));
  }

  /** Null text amounts are rejected before parsing. */
  @Test
  void shouldRejectNullTextAmount() {
    assertThrows(InvalidDomainArgumentException.class,
        () -> Money.of((String) null, CHF));
  }

  /** Invalid decimal text is rejected rather than coerced. */
  @Test
  void shouldRejectInvalidDecimalText() {
    assertThrows(NumberFormatException.class, () -> Money.of("NaN", CHF));
  }

  /** Null multiplication factors are rejected by the domain validator. */
  @Test
  void shouldRejectNullDecimalFactor() {
    assertThrows(InvalidDomainArgumentException.class,
        () -> Money.of("1.00", CHF).multiply((BigDecimal) null));
  }

  /** Large positive long amounts are represented without double precision loss. */
  @Test
  void shouldAcceptMaximumLongAmount() {
    assertEquals(new BigDecimal("9223372036854775807.00"),
        Money.of(Long.MAX_VALUE, CHF).amount());
  }

  /** Large negative long amounts are represented without double precision loss. */
  @Test
  void shouldAcceptMinimumLongAmount() {
    assertEquals(new BigDecimal("-9223372036854775808.00"),
        Money.of(Long.MIN_VALUE, CHF).amount());
  }

  /** The long factory interprets whole major units, not cents. */
  @Test
  void shouldInterpretLongAsWholeMajorUnits() {
    assertEquals(Money.of("100.00", CHF), Money.of(100L, CHF));
  }
}
