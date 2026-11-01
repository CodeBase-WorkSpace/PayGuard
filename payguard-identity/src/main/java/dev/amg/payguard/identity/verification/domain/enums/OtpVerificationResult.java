package dev.amg.payguard.identity.verification.domain.enums;

public enum OtpVerificationResult {
  VERIFIED,
  INVALID_CODE,
  EXPIRED,
  ALREADY_USED,
  LOCKED,
  INVALIDATED,
  PURPOSE_MISMATCH,
  DEVICE_MISMATCH
}
