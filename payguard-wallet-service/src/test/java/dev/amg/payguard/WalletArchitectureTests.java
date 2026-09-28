package dev.amg.payguard;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class WalletArchitectureTests {
  private final ApplicationModules modules = ApplicationModules.of(WalletApp.class);

  @Test
  void verifiesApplicationModuleBoundaries() {
    modules.verify();
  }
}
