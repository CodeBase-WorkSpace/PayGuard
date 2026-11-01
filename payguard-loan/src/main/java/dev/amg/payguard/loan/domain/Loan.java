package dev.amg.payguard.loan.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings({
  "PMD.AvoidFieldNameMatchingMethodName",
  "PMD.AvoidLiteralsInIfCondition",
  "PMD.AvoidReassigningParameters"
})
public final class Loan {
  private final UUID id;
  private final String borrowerId;
  private final UUID collateralId;
  private final BigDecimal principal;
  private final BigDecimal annualRate;
  private final int termMonths;
  private LoanStatus status;
  private BigDecimal outstandingPrincipal;
  private BigDecimal accruedInterest = BigDecimal.ZERO;
  private LocalDate lastAccrualDate;
  private final List<Installment> installments;
  private final List<Repayment> repayments = new ArrayList<>();

  public Loan(
      UUID id,
      String borrowerId,
      UUID collateralId,
      BigDecimal principal,
      BigDecimal annualRate,
      int termMonths,
      LocalDate startDate,
      List<Installment> installments,
      LoanStatus status) {
    this.id = Objects.requireNonNull(id);
    this.borrowerId = Objects.requireNonNull(borrowerId);
    this.collateralId = Objects.requireNonNull(collateralId);
    this.principal = positive(principal);
    this.annualRate = nonNegative(annualRate);
    if (termMonths < 1) throw new IllegalArgumentException("term");
    this.termMonths = termMonths;
    this.lastAccrualDate = Objects.requireNonNull(startDate);
    this.installments = new ArrayList<>(Objects.requireNonNull(installments));
    this.status = Objects.requireNonNull(status);
    this.outstandingPrincipal = this.principal;
  }

  public static Loan originate(
      UUID id,
      String borrowerId,
      UUID collateralId,
      BigDecimal principal,
      BigDecimal annualRate,
      int termMonths,
      LocalDate start) {
    return new Loan(
        id,
        borrowerId,
        collateralId,
        principal,
        annualRate,
        termMonths,
        start,
        schedule(principal, annualRate, termMonths, start),
        LoanStatus.PENDING);
  }

  private static List<Installment> schedule(BigDecimal p, BigDecimal rate, int n, LocalDate start) {
    BigDecimal monthly = rate.divide(BigDecimal.valueOf(12), 18, RoundingMode.HALF_EVEN);
    BigDecimal emi =
        monthly.signum() == 0
            ? p.divide(BigDecimal.valueOf(n), 18, RoundingMode.HALF_EVEN)
            : p.multiply(monthly)
                .multiply(monthly.add(BigDecimal.ONE).pow(n))
                .divide(
                    monthly.add(BigDecimal.ONE).pow(n).subtract(BigDecimal.ONE),
                    18,
                    RoundingMode.HALF_EVEN);
    BigDecimal balance = p;
    List<Installment> result = new ArrayList<>();
    for (int i = 1; i <= n; i++) {
      BigDecimal interest = balance.multiply(monthly).setScale(18, RoundingMode.HALF_EVEN);
      BigDecimal principalDue = i == n ? balance : emi.subtract(interest).max(BigDecimal.ZERO);
      balance = balance.subtract(principalDue);
      result.add(
          new Installment(
              UUID.randomUUID(), i, start.plusMonths(i), principalDue, interest, BigDecimal.ZERO));
    }
    return result;
  }

  public void activate() {
    require(LoanStatus.PENDING);
    status = LoanStatus.ACTIVE;
  }

  public void accrueUntil(LocalDate date) {
    requireOpen();
    if (date.isBefore(lastAccrualDate)) throw new IllegalArgumentException("date before accrual");
    accruedInterest =
        accruedInterest.add(
            InterestAccrual.actual365(outstandingPrincipal, annualRate, lastAccrualDate, date));
    lastAccrualDate = date;
  }

  public Repayment repay(BigDecimal amount, Clock clock) {
    requireOpen();
    amount = positive(amount);
    BigDecimal interest = amount.min(accruedInterest);
    BigDecimal principalPaid = amount.subtract(interest).min(outstandingPrincipal);
    accruedInterest = accruedInterest.subtract(interest);
    outstandingPrincipal = outstandingPrincipal.subtract(principalPaid);
    repayments.add(new Repayment(UUID.randomUUID(), interest, principalPaid, clock.instant()));
    if (outstandingPrincipal.signum() == 0 && accruedInterest.signum() == 0)
      status = LoanStatus.REPAID;
    return repayments.getLast();
  }

  public void markDelinquent() {
    if (status == LoanStatus.ACTIVE) status = LoanStatus.DELINQUENT;
  }

  public void markLiquidated(BigDecimal proceeds) {
    requireOpen();
    if (proceeds.compareTo(outstandingPrincipal.add(accruedInterest)) >= 0) {
      outstandingPrincipal = BigDecimal.ZERO;
      accruedInterest = BigDecimal.ZERO;
      status = LoanStatus.LIQUIDATED;
    } else {
      outstandingPrincipal = outstandingPrincipal.subtract(proceeds.max(BigDecimal.ZERO));
      status = LoanStatus.DEFAULTED;
    }
  }

  public void dispute() {
    if (status == LoanStatus.PENDING) status = LoanStatus.DISPUTED;
  }

  private void requireOpen() {
    if (status != LoanStatus.ACTIVE && status != LoanStatus.DELINQUENT)
      throw new IllegalStateException("loan is not open");
  }

  private void require(LoanStatus expected) {
    if (status != expected)
      throw new IllegalStateException("expected " + expected + " but was " + status);
  }

  private static BigDecimal positive(BigDecimal v) {
    if (v == null || v.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
    return v;
  }

  private static BigDecimal nonNegative(BigDecimal v) {
    if (v == null || v.signum() < 0)
      throw new IllegalArgumentException("rate must be non-negative");
    return v;
  }

  public UUID id() {
    return id;
  }

  public String borrowerId() {
    return borrowerId;
  }

  public UUID collateralId() {
    return collateralId;
  }

  public BigDecimal principal() {
    return principal;
  }

  public BigDecimal annualRate() {
    return annualRate;
  }

  public int termMonths() {
    return termMonths;
  }

  public LoanStatus status() {
    return status;
  }

  public BigDecimal outstandingPrincipal() {
    return outstandingPrincipal;
  }

  public BigDecimal accruedInterest() {
    return accruedInterest;
  }

  public List<Installment> installments() {
    return List.copyOf(installments);
  }

  public List<Repayment> repayments() {
    return List.copyOf(repayments);
  }
}
