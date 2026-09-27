package dev.amg.payguard.collateral.domain;

public enum CollateralStatus {
  AVAILABLE,
  LOCKED,
  MARGIN_CALL,
  LIQUIDATING,
  LIQUIDATED,
  RELEASED
}
