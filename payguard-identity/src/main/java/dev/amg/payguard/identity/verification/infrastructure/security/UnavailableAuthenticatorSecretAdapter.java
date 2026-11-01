package dev.amg.payguard.identity.verification.infrastructure.security;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.verification.application.port.AuthenticatorSecretPort;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Resolves no secrets by default; production wiring supplies a vault-backed implementation. */
@Component
@ConditionalOnProperty(name = "payguard.vault.enabled", havingValue = "false")
public final class UnavailableAuthenticatorSecretAdapter implements AuthenticatorSecretPort {

  @Override
  public Optional<String> secretFor(CustomerId customerId) {
    return Optional.empty();
  }
}
