package dev.amg.payguard.identity.verification.application.port;

@FunctionalInterface
public interface OtpDelivery {
  /**
   * Submits to the configured adapter; completion is not proof of receipt by the customer. The
   * local mock deliberately discards the payload. Failures propagate to the use case.
   */
  void submit(OtpDeliveryRequest request);
}
