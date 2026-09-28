package dev.amg.payguard.otp.infrastructure.security;

import java.util.Optional;

@FunctionalInterface
public interface AuthenticatorSecretPort {

  Optional<String> secretFor(String userId);
}
