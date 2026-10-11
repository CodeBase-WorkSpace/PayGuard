package dev.amg.payguard.identity.verification.domain.event;

@FunctionalInterface
public interface OtpEventPublisher {
  void publish(OtpEvent event);
}
