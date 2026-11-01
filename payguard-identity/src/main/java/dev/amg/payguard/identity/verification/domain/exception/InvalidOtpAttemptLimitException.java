package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;

public final class InvalidOtpAttemptLimitException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  private static final String MESSAGE =
      "OTP max attempts must be greater than or equal to %d, but was %d.";

  public InvalidOtpAttemptLimitException(int maxAttempts, int minAttempts) {
    super(MESSAGE.formatted(maxAttempts, minAttempts));
  }
}
