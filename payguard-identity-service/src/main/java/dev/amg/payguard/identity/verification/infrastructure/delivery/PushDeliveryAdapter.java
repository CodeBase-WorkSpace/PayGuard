package dev.amg.payguard.identity.verification.infrastructure.delivery;

import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** TLS provider integration seam for push notifications. */
@Component
@ConditionalOnProperty(name = "identity.delivery.mode", havingValue = "push")
public class PushDeliveryAdapter implements OtpDelivery {

  @Override
  public void submit(OtpDeliveryRequest message) {
    throw new UnsupportedOperationException("Delivery provider is not configured");
  }
}
