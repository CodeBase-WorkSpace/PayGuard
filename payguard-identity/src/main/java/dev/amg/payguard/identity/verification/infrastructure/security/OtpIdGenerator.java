package dev.amg.payguard.identity.verification.infrastructure.security;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public final class OtpIdGenerator {
  private static final String ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private final SecureRandom random = new SecureRandom();

  public OtpId generate() {
    StringBuilder value = new StringBuilder("otp_");
    for (int index = 0; index < 22; index++) {
      value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return OtpId.parse(value.toString());
  }
}
