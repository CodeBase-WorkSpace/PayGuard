package dev.amg.payguard.collateral.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollateralDomainTest {
  @Test
  void crossesMarginAndLiquidationThresholds() {
    Collateral c =
        new Collateral(
            UUID.randomUUID(),
            "owner-token",
            UUID.randomUUID(),
            new BigDecimal("1000"),
            "USD",
            new BigDecimal(".60"),
            new BigDecimal(".70"),
            new BigDecimal(".80"));
    c.lock();
    assertEquals(
        CollateralStatus.MARGIN_CALL,
        c.updateValuation(
                new Valuation(new BigDecimal("1400"), new BigDecimal("1"), Instant.now()),
                new BigDecimal("1000"))
            .status());
    assertEquals(
        CollateralStatus.LIQUIDATING,
        c.updateValuation(
                new Valuation(new BigDecimal("1200"), new BigDecimal("1"), Instant.now()),
                new BigDecimal("1000"))
            .status());
  }
}
