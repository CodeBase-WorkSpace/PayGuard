package dev.amg.payguard.otp.domain;

public interface RateLimiter {

  boolean allowIssue(String userId, OtpPurpose purpose, String sourceIp);

  boolean allowVerify(String userId, OtpPurpose purpose, String sourceIp);
}
