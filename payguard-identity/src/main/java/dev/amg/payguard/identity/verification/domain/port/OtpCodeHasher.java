package dev.amg.payguard.identity.verification.domain.port;

public interface OtpCodeHasher {
  String hash(String value);

  boolean matches(String value, String encodedHash);
}
