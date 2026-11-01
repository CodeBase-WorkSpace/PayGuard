package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;

public final class OtpAttemptCountOutOfRangeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  public static final String MESSAGE = "OTP attempts must be between 0 and %d, but was %d.";

  public OtpAttemptCountOutOfRangeException(int attempts, int maxAttempts) {
    super(MESSAGE.formatted(maxAttempts, attempts));
  }
}
