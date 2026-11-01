package dev.amg.payguard.identity.verification.application.command.otp_verification.exception;

import java.io.Serial;

/** Raised when an OTP verification command has invalid input. */
public final class InvalidOtpVerificationCommandException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidOtpVerificationCommandException(String reason) {
    super("Invalid OTP verification request: " + reason);
  }
}
