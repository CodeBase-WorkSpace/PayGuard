package dev.amg.payguard.identity.verification.domain.exception;

import java.io.Serial;

public final class OtpDeviceBindingHashMustNotBeBlankException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;
  public static final String MESSAGE = "OTP device binding hash must not be blank.";

  public OtpDeviceBindingHashMustNotBeBlankException() {
    super(MESSAGE);
  }
}
