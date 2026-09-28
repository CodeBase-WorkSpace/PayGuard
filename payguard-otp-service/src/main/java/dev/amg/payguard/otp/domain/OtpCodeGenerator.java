package dev.amg.payguard.otp.domain;

@FunctionalInterface
public interface OtpCodeGenerator {

  String generate();
}
