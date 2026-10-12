package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import java.time.Instant;

/**
 * customerId is the challenge subject, not an authenticated actor. No trusted actor or correlation
 * context is currently supplied by the inbound contract.
 */
public record VerificationAuditEntry(
    OtpId challengeId,
    CustomerId customerId,
    Action action,
    String reasonCode,
    String sourceIp,
    Instant occurredAt) {
  public enum Action {
    ISSUED,
    VERIFY
  }
}
