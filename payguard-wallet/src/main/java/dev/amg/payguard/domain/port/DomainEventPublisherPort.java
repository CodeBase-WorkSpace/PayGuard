package dev.amg.payguard.domain.port;

import domain.DomainEvent;
import java.util.List;

public interface DomainEventPublisherPort {
  void publish(DomainEvent event);

  void publishAll(List<DomainEvent> events);
}
