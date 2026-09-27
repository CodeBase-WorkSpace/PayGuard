package dev.amg.payguard.collateral.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@FunctionalInterface
public interface PriceOracle {
  Quote quote(UUID collateralId, Currency assetCurrency, Currency loanCurrency, Instant now);

  record Quote(BigDecimal assetValue, BigDecimal fxRate, Instant observedAt) {}
}
