package dev.amg.payguard.loan.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record Installment(
    UUID id,
    int number,
    LocalDate dueDate,
    BigDecimal principalDue,
    BigDecimal interestDue,
    BigDecimal paidAmount) {
  public Installment {
    Objects.requireNonNull(id);
    Objects.requireNonNull(dueDate);
    Objects.requireNonNull(principalDue);
    Objects.requireNonNull(interestDue);
    Objects.requireNonNull(paidAmount);
    if (number < 1
        || principalDue.signum() < 0
        || interestDue.signum() < 0
        || paidAmount.signum() < 0) throw new IllegalArgumentException("invalid installment");
  }

  public BigDecimal totalDue() {
    return principalDue.add(interestDue);
  }

  public BigDecimal remaining() {
    return totalDue().subtract(paidAmount).max(BigDecimal.ZERO);
  }

  public Installment apply(BigDecimal amount) {
    return new Installment(
        id, number, dueDate, principalDue, interestDue, paidAmount.add(amount.min(remaining())));
  }
}
