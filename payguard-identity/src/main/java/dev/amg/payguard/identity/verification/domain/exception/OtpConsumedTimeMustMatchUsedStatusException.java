package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;
import java.time.Instant;

public final class OtpConsumedTimeMustMatchUsedStatusException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  public static final String MESSAGE =
      """
            OTP consumption time must be within the challenge validity period. \
           consumedAt=%s, createdAt=%s, expiresAt=%s.""";

  public OtpConsumedTimeMustMatchUsedStatusException(
      Instant consumedAt, Instant createdAt, Instant expiresAt) {
    super(MESSAGE.formatted(consumedAt, createdAt, expiresAt));
  }
}
