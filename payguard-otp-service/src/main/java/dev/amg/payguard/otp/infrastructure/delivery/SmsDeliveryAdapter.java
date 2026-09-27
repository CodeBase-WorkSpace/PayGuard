package dev.amg.payguard.otp.infrastructure.delivery;

import dev.amg.payguard.otp.domain.DeliveryPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** TLS provider integration seam for SMS. The provider request must never be logged. */
@Component
@ConditionalOnProperty(name = "otp.delivery.mode", havingValue = "sms")
public class SmsDeliveryAdapter implements DeliveryPort {

  @Override
  public void deliver(DeliveryMessage message) {
    // Integrate with the SMS provider over TLS; do not log message.code.
  }
}
