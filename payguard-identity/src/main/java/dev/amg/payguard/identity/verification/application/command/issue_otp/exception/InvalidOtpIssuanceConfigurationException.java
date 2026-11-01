package dev.amg.payguard.identity.verification.application.command.issue_otp.exception;

/** Raised when OTP issuance configuration violates required constraints. */
public final class InvalidOtpIssuanceConfigurationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidOtpIssuanceConfigurationException(String reason) {
    super("Invalid OTP issuance configuration: " + reason);
  }
}
