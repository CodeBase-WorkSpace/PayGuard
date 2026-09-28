package dev.amg.payguard.collateral.application;

import dev.amg.payguard.collateral.domain.Collateral;
import java.util.Optional;
import java.util.UUID;

public interface CollateralRepository {
  Collateral save(Collateral collateral);

  Optional<Collateral> find(UUID id);
}
