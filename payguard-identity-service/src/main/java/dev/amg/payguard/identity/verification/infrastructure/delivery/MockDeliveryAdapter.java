package dev.amg.payguard.identity.verification.infrastructure.delivery;

import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.OtpDeliveryRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Safe local adapter. It deliberately discards the code and never logs it. A provider adapter is
 * selected in production without changing the domain or application layer.
 */
@Component
@ConditionalOnProperty(name = "identity.delivery.mode", havingValue = "mock", matchIfMissing = true)
public class MockDeliveryAdapter implements OtpDelivery {

  @Override
  public void submit(OtpDeliveryRequest message) {
    // Provider calls belong here. Never log or persist message.code.
  }
}
