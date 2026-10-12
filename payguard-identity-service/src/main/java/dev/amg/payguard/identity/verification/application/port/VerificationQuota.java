package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;

@FunctionalInterface
public interface VerificationQuota {
  /**
   * Consumes one request in the fixed window, including a denied request. Returns false on
   * exhausted quota; storage failures propagate.
   */
  boolean tryAcquire(Request request);

  record Request(CustomerId customerId, OtpPurpose purpose, String sourceIp, Operation operation) {}

  enum Operation {
    ISSUE,
    VERIFY
  }
}
