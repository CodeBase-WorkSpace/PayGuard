package dev.amg.payguard.identity.verification.infrastructure.security;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.verification.application.port.AuthenticatorSecretPort;
import infrastructure.vault.VaultClient;
import infrastructure.vault.VaultUnavailableException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Reads a scoped provisioned seed; absence is distinct from a dependency or policy failure. */
@Component
@ConditionalOnProperty(name = "payguard.vault.enabled", havingValue = "true")
public final class VaultAuthenticatorSecretAdapter implements AuthenticatorSecretPort {
  private final VaultClient vault;
  private final String prefix;

  public VaultAuthenticatorSecretAdapter(
      VaultClient vault,
      @org.springframework.beans.factory.annotation.Value("${payguard.vault.secret-prefix}")
          String prefix) {
    this.vault = vault;
    if (!prefix.matches("[a-z][a-z0-9-]*/identity")) {
      throw new IllegalArgumentException("Invalid Identity secret scope");
    }
    this.prefix = prefix;
  }

  @Override
  public Optional<String> secretFor(CustomerId customerId) {
    return vault
        .readOptional("secret/data/" + prefix + "/authenticators/" + customerId.getValue())
        .map(
            response -> {
              String seed = response.path("data").path("data").path("seed").asText();
              if (!seed.matches("[A-Z2-7]{16,128}={0,6}")) {
                throw new VaultUnavailableException();
              }
              return seed;
            });
  }
}
