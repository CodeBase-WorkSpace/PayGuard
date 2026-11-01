package dev.amg.payguard.identity.verification.domain.policy;

import dev.amg.payguard.identity.verification.domain.exception.OtpRetentionPeriodMustNotPrecedeExpirationException;
import java.time.Instant;
import java.time.ZoneOffset;

public final class OtpRetentionPolicy {
  private OtpRetentionPolicy() {
    /* This utility class should not be instantiated */
  }

  private static final int MIN_RETENTION_YEARS = 3;

  private static Instant minimumRetentionUntil(Instant expiresAt) {
    return expiresAt.atZone(ZoneOffset.UTC).plusYears(MIN_RETENTION_YEARS).toInstant();
  }

  public static void validate(Instant expiresAt, Instant retentionUntil) {
    if (retentionUntil.isBefore(minimumRetentionUntil(expiresAt))) {
      throw new OtpRetentionPeriodMustNotPrecedeExpirationException(retentionUntil, expiresAt);
    }
  }
}
