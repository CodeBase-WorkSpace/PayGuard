package dev.amg.payguard.identity.verification.domain.port;

@FunctionalInterface
public interface OtpCodeGenerator {
  String generate();
}
