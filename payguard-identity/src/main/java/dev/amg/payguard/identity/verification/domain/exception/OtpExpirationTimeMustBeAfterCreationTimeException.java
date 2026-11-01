package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;

public final class OtpExpirationTimeMustBeAfterCreationTimeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  private static final String MESSAGE = "OTP expiration time must be after the creation time.";

  public OtpExpirationTimeMustBeAfterCreationTimeException() {
    super(MESSAGE);
  }
}
