package dev.amg.payguard.identity.verification.application.command.otp_verification.exception;

import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import java.io.Serial;

/** Raised when an OTP uses a verification mode unsupported by this use case. */
public final class UnsupportedOtpVerificationModeException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public UnsupportedOtpVerificationModeException(OtpVerificationMode mode) {
    super("Unsupported OTP verification mode: " + mode);
  }
}
