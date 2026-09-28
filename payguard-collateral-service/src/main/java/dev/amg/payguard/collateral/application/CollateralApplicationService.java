package dev.amg.payguard.collateral.application;

import dev.amg.payguard.collateral.domain.Collateral;
import dev.amg.payguard.collateral.domain.CollateralStatus;
import dev.amg.payguard.collateral.domain.PriceOracle;
import dev.amg.payguard.collateral.domain.Valuation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CollateralApplicationService {
  private final CollateralRepository repository;
  private final CollateralLiquidationPort liquidation;

  public CollateralApplicationService(
      CollateralRepository repository, CollateralLiquidationPort liquidation) {
    this.repository = repository;
    this.liquidation = liquidation;
  }

  public Collateral lock(Collateral collateral) {
    collateral.lock();
    return repository.save(collateral);
  }

  public Collateral refresh(
      UUID id,
      BigDecimal exposure,
      PriceOracle oracle,
      java.util.Currency loanCurrency,
      Instant now) {
    Collateral c = repository.find(id).orElseThrow();
    PriceOracle.Quote q =
        oracle.quote(id, java.util.Currency.getInstance(c.assetCurrency()), loanCurrency, now);
    Collateral.Risk risk =
        c.updateValuation(new Valuation(q.assetValue(), q.fxRate(), q.observedAt()), exposure);
    if (risk.status() == CollateralStatus.LIQUIDATING)
      liquidation.liquidate(
          id,
          exposure,
          c.assetCurrency(),
          loanCurrency.getCurrencyCode(),
          id.toString() + ":" + q.observedAt());
    return repository.save(c);
  }
}
