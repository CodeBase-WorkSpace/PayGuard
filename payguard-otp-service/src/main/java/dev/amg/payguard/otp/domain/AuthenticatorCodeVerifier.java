package dev.amg.payguard.otp.domain;

import java.time.Instant;

@FunctionalInterface
public interface AuthenticatorCodeVerifier {

  boolean verify(String userId, String code, Instant at);
}
