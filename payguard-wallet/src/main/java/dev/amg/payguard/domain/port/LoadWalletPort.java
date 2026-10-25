package dev.amg.payguard.domain.port;

import dev.amg.payguard.domain.model.Wallet;
import dev.amg.payguard.domain.value_object.UserId;
import dev.amg.payguard.domain.value_object.WalletId;
import java.util.Currency;
import java.util.Optional;

public interface LoadWalletPort {
  Optional<Wallet> findById(WalletId id);

  Optional<Wallet> findByIdForUpdate(WalletId id);

  boolean existsByUserIdAndCurrency(UserId userId, Currency currency);
}
