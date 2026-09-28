package dev.amg.payguard.otp.infrastructure.security;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves no secrets by default; production wiring supplies a vault-backed implementation. */
@Component
public final class UnavailableAuthenticatorSecretAdapter implements AuthenticatorSecretPort {

  @Override
  public Optional<String> secretFor(String userId) {
    return Optional.empty();
  }
}
