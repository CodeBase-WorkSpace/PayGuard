package dev.amg.payguard.collateral.application;

import java.math.BigDecimal;
import java.util.UUID;

@FunctionalInterface
public interface CollateralLiquidationPort {
  void liquidate(
      UUID collateralId, BigDecimal amount, String assetCurrency, String loanCurrency, String key);
}
