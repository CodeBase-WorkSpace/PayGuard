package dev.amg.payguard.identity.verification.application.command.issue_otp.exception;

import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;

/** Raised when OTP issuance is requested through an unsupported channel. */
public final class UnsupportedOtpChannelException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public UnsupportedOtpChannelException(OtpChannel channel) {
    super("OTP issuance is not supported for channel: " + channel);
  }
}
