# ADR-0005: Cash collateral LTV policy

The demo policy uses 50% origination, 60% warning, 70% margin call, and 80% liquidation thresholds, with a 60% target after partial liquidation. Cash collateral is held in a wallet reservation. Foreign-currency cash is valued through a price-oracle port; the supplied adapter is simulated and records its policy version and quote timestamp. Stale or missing quotes suspend execution.
