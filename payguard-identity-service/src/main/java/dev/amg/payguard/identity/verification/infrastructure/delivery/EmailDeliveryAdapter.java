package dev.amg.payguard.identity.verification.infrastructure.delivery;

import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** TLS provider integration seam for email. The provider request must never be logged. */
@Component
@ConditionalOnProperty(name = "identity.delivery.mode", havingValue = "email")
public class EmailDeliveryAdapter implements OtpDelivery {

  @Override
  public void submit(OtpDeliveryRequest message) {
    throw new UnsupportedOperationException("Delivery provider is not configured");
  }
}
