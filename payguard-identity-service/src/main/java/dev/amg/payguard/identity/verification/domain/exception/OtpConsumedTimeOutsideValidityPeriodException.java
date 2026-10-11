package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;
import java.time.Instant;

public final class OtpConsumedTimeOutsideValidityPeriodException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  public static final String MESSAGE =
      """
           OTP consumption time must be within the validity period. \
           consumedAt=%s, createdAt=%s, expiresAt=%s""";

  public OtpConsumedTimeOutsideValidityPeriodException(
      Instant consumedAt, Instant createdAt, Instant expiresAt) {
    super(MESSAGE.formatted(consumedAt, createdAt, expiresAt));
  }
}
