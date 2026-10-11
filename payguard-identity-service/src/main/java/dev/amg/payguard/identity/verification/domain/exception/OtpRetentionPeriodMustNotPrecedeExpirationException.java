package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;
import java.time.Instant;

public class OtpRetentionPeriodMustNotPrecedeExpirationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public static final String MESSAGE =
      "OTP retention period is invalid: retentionUntil must be after expiresAt. "
          + "The OTP record cannot be scheduled for removal before or at the same time as its expiration. "
          + "retentionUntil=%s, expiresAt=%s";

  public OtpRetentionPeriodMustNotPrecedeExpirationException(
      Instant retentionUntil, Instant expiresAt) {
    super(MESSAGE.formatted(retentionUntil, expiresAt));
  }
}
