package dev.amg.payguard.collateral.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Valuation(BigDecimal amount, BigDecimal fxRate, Instant observedAt) {
  public Valuation {
    if (amount == null
        || amount.signum() < 0
        || fxRate == null
        || fxRate.signum() <= 0
        || observedAt == null) throw new IllegalArgumentException("invalid valuation");
  }
}
