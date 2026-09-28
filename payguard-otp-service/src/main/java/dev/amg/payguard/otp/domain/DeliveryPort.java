package dev.amg.payguard.otp.domain;

@FunctionalInterface
public interface DeliveryPort {

  void deliver(DeliveryMessage message);

  record DeliveryMessage(
      java.util.UUID challengeId,
      String userId,
      OtpPurpose purpose,
      OtpChannel channel,
      String destinationRef,
      String code) {}
}
