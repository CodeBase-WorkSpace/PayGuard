package dev.amg.payguard.loan.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;

public final class InterestAccrual {
  private InterestAccrual() {}

  public static BigDecimal actual365(
      BigDecimal principal, BigDecimal annualRate, LocalDate from, LocalDate to) {
    if (to.isBefore(from)) throw new IllegalArgumentException("to before from");
    long days = to.toEpochDay() - from.toEpochDay();
    return principal
        .multiply(annualRate, MathContext.DECIMAL128)
        .multiply(BigDecimal.valueOf(days), MathContext.DECIMAL128)
        .divide(BigDecimal.valueOf(365), 16, RoundingMode.HALF_EVEN);
  }
}
