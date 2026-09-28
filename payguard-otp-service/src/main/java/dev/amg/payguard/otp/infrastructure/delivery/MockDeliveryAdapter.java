package dev.amg.payguard.otp.infrastructure.delivery;

import dev.amg.payguard.otp.domain.DeliveryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Safe local adapter. It deliberately discards the code and never logs it. A provider adapter is
 * selected in production without changing the domain or application layer.
 */
@Component
@ConditionalOnProperty(name = "otp.delivery.mode", havingValue = "mock", matchIfMissing = true)
public class MockDeliveryAdapter implements DeliveryPort {

  @Override
  public void deliver(DeliveryMessage message) {
    // Provider calls belong here. Never log or persist message.code.
  }
}
