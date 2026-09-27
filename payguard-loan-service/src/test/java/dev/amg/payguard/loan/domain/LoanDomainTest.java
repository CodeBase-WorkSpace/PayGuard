package dev.amg.payguard.loan.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoanDomainTest {
  @Test
  void generatesScheduleAndAccruesActual365() {
    Loan loan =
        Loan.originate(
            UUID.randomUUID(),
            "subject-token",
            UUID.randomUUID(),
            new BigDecimal("1200"),
            new BigDecimal("0.12"),
            12,
            LocalDate.of(2026, 1, 1));
    assertEquals(12, loan.installments().size());
    loan.activate();
    loan.accrueUntil(LocalDate.of(2026, 2, 1));
    assertTrue(loan.accruedInterest().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void allocatesInterestBeforePrincipal() {
    Loan loan =
        Loan.originate(
            UUID.randomUUID(),
            "s",
            UUID.randomUUID(),
            new BigDecimal("1000"),
            new BigDecimal("0.10"),
            12,
            LocalDate.of(2026, 1, 1));
    loan.activate();
    loan.accrueUntil(LocalDate.of(2026, 2, 1));
    BigDecimal before = loan.outstandingPrincipal();
    Repayment r =
        loan.repay(
            new BigDecimal("20"),
            Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC));
    assertTrue(r.interest().signum() > 0);
    assertTrue(loan.outstandingPrincipal().compareTo(before) < 0);
  }
}
