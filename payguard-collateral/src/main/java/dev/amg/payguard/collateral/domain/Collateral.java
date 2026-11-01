package dev.amg.payguard.collateral.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings({"PMD.AvoidFieldNameMatchingMethodName"})
public final class Collateral {
  private final UUID id;
  private final String ownerId;
  private final UUID loanId;
  private final BigDecimal amount;
  private final String assetCurrency;
  private final BigDecimal warningLtv;
  private final BigDecimal marginLtv;
  private final BigDecimal liquidationLtv;
  private CollateralStatus status;
  private Valuation latest;
  private final List<Valuation> history = new ArrayList<>();

  public Collateral(
      UUID id,
      String ownerId,
      UUID loanId,
      BigDecimal amount,
      String assetCurrency,
      BigDecimal warningLtv,
      BigDecimal marginLtv,
      BigDecimal liquidationLtv) {
    this.id = Objects.requireNonNull(id);
    this.ownerId = Objects.requireNonNull(ownerId);
    this.loanId = Objects.requireNonNull(loanId);
    if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount");
    this.amount = amount;
    this.assetCurrency = Objects.requireNonNull(assetCurrency);
    this.warningLtv = threshold(warningLtv);
    this.marginLtv = threshold(marginLtv);
    this.liquidationLtv = threshold(liquidationLtv);
    if (this.warningLtv.compareTo(this.marginLtv) >= 0
        || this.marginLtv.compareTo(this.liquidationLtv) >= 0)
      throw new IllegalArgumentException("threshold order");
    status = CollateralStatus.AVAILABLE;
  }

  public void lock() {
    if (status != CollateralStatus.AVAILABLE)
      throw new IllegalStateException("collateral unavailable");
    status = CollateralStatus.LOCKED;
  }

  public Risk updateValuation(Valuation valuation, BigDecimal exposure) {
    Objects.requireNonNull(valuation);
    if (status == CollateralStatus.RELEASED || status == CollateralStatus.LIQUIDATED)
      throw new IllegalStateException("collateral closed");
    latest = valuation;
    history.add(valuation);
    BigDecimal ltv =
        exposure.signum() == 0
            ? BigDecimal.ZERO
            : exposure.divide(valuation.amount(), 18, RoundingMode.HALF_EVEN);
    if (ltv.compareTo(liquidationLtv) >= 0) status = CollateralStatus.LIQUIDATING;
    else if (ltv.compareTo(marginLtv) >= 0) status = CollateralStatus.MARGIN_CALL;
    else if (status == CollateralStatus.MARGIN_CALL && ltv.compareTo(warningLtv) < 0)
      status = CollateralStatus.LOCKED;
    return new Risk(ltv, status);
  }

  public void markLiquidated() {
    if (status != CollateralStatus.LIQUIDATING && status != CollateralStatus.MARGIN_CALL)
      throw new IllegalStateException("not liquidatable");
    status = CollateralStatus.LIQUIDATED;
  }

  public void release() {
    if (status != CollateralStatus.LOCKED && status != CollateralStatus.MARGIN_CALL)
      throw new IllegalStateException("not releasable");
    status = CollateralStatus.RELEASED;
  }

  private static BigDecimal threshold(BigDecimal v) {
    if (v == null || v.signum() < 0 || v.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException("LTV threshold");
    return v;
  }

  public record Risk(BigDecimal ltv, CollateralStatus status) {}

  public UUID id() {
    return id;
  }

  public String ownerId() {
    return ownerId;
  }

  public UUID loanId() {
    return loanId;
  }

  public BigDecimal amount() {
    return amount;
  }

  public String assetCurrency() {
    return assetCurrency;
  }

  public CollateralStatus status() {
    return status;
  }

  public Valuation latest() {
    return latest;
  }

  public List<Valuation> history() {
    return List.copyOf(history);
  }
}
