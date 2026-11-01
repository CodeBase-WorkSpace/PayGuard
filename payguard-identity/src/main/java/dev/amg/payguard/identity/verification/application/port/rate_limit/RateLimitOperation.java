package dev.amg.payguard.identity.verification.application.port.rate_limit;

/** Identifies OTP operations subject to rate limiting. */
public enum RateLimitOperation {
  ISSUE,
  VERIFY,
  RESEND
}
