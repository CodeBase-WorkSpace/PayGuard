package dev.amg.payguard.identity.verification.domain.exception;

import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import java.io.Serial;

public final class OtpCodeHashRequiredForRandomNumericVerificationException
    extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public static final String MESSAGE = "OTP code hash is required for %s verification mode.";

  public OtpCodeHashRequiredForRandomNumericVerificationException(
      OtpVerificationMode verificationMode) {
    super(MESSAGE.formatted(verificationMode));
  }
}
