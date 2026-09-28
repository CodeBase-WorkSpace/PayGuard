package dev.amg.payguard.otp.domain;

public enum OtpChallengeStatus {
  ACTIVE,
  USED,
  EXPIRED,
  LOCKED,
  INVALIDATED
}
