package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import java.util.Optional;

@FunctionalInterface
public interface AuthenticatorSecretPort {

  Optional<String> secretFor(CustomerId customerId);
}
