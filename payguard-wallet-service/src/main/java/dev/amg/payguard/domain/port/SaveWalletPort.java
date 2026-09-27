package dev.amg.payguard.domain.port;

import dev.amg.payguard.domain.model.Wallet;

@FunctionalInterface
public interface SaveWalletPort {
  Wallet save(Wallet wallet);
}
