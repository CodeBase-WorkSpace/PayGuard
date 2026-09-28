package dev.amg.payguard.otp.domain;

public interface OtpCodeHasher {

  String hash(String value);

  boolean matches(String value, String encodedHash);
}
