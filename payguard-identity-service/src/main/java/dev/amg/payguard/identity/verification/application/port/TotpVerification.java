package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import java.time.Instant;

/**
 * Server-side TOTP verification using a trusted secret source. Retained for the existing challenge
 * flow; ownership of LOGIN MFA remains unresolved pending provider integration.
 */
@FunctionalInterface
public interface TotpVerification {
  boolean verify(CustomerId customerId, String code, Instant at);
}
