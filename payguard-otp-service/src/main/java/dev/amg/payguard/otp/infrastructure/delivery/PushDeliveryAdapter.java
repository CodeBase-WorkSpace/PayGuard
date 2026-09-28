package dev.amg.payguard.otp.infrastructure.delivery;

import dev.amg.payguard.otp.domain.DeliveryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** TLS provider integration seam for push notifications. */
@Component
@ConditionalOnProperty(name = "otp.delivery.mode", havingValue = "push")
public class PushDeliveryAdapter implements DeliveryPort {

  @Override
  public void deliver(DeliveryMessage message) {
    // Integrate with the push provider over TLS; do not log message.code.
  }
}
