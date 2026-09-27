package dev.amg.payguard.otp.infrastructure.security;

import dev.amg.payguard.otp.domain.OtpCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public final class SecureNumericOtpGenerator implements OtpCodeGenerator {

  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    return "%06d".formatted(random.nextInt(1_000_000));
  }
}
