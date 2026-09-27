package dev.amg.payguard.domain.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockPort {
  Instant now();
}
