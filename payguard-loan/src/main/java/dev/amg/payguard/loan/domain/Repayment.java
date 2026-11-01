package dev.amg.payguard.loan.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Repayment(UUID id, BigDecimal interest, BigDecimal principal, Instant recordedAt) {
  public Repayment {
    Objects.requireNonNull(id);
    Objects.requireNonNull(interest);
    Objects.requireNonNull(principal);
    Objects.requireNonNull(recordedAt);
    if (interest.signum() < 0 || principal.signum() < 0)
      throw new IllegalArgumentException("repayment amounts must be non-negative");
  }

  public BigDecimal total() {
    return interest.add(principal);
  }
}
