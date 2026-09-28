package dev.amg.payguard.domain.port;

import dev.amg.payguard.domain.model.Hold;
import dev.amg.payguard.domain.value_object.HoldId;
import dev.amg.payguard.domain.value_object.WalletId;
import java.util.List;
import java.util.Optional;

public interface HoldRepositoryPort {
  Optional<Hold> findById(HoldId id);

  List<Hold> findPendingHoldsByWalletId(WalletId walletId);

  Hold save(Hold hold);
}
