package dev.amg.payguard.collateral.application;

import java.util.Map;
import java.util.UUID;

/** Restricted port used by the identity/privacy service for Articles 15, 17 and 20 requests. */
public interface CollateralDataExportPort {
  Map<String, Object> export(UUID collateralId);

  void pseudonymize(UUID collateralId, String retainedToken);
}
