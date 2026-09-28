package dev.amg.payguard.otp.infrastructure.security;

import dev.amg.payguard.otp.domain.OtpCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class HmacOtpCodeHasher implements OtpCodeHasher {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int HASH_PARTS = 2;
  private final SecureRandom secureRandom = new SecureRandom();
  private final byte[] pepper;

  public HmacOtpCodeHasher(@Value("${otp.security.pepper}") String pepper) {
    if (pepper == null || pepper.isBlank() || pepper.length() < 32) {
      throw new IllegalArgumentException("otp.security.pepper must contain at least 32 characters");
    }
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String hash(String value) {
    byte[] salt = new byte[16];
    secureRandom.nextBytes(salt);
    return HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(digest(value, salt));
  }

  @Override
  public boolean matches(String value, String encodedHash) {
    if (value == null || encodedHash == null) {
      return false;
    }
    try {
      String[] parts = encodedHash.split(":", -1);
      if (parts.length != HASH_PARTS) {
        return false;
      }
      byte[] salt = HexFormat.of().parseHex(parts[0]);
      byte[] expected = HexFormat.of().parseHex(parts[1]);
      return MessageDigest.isEqual(expected, digest(value, salt));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private byte[] digest(String value, byte[] salt) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(pepper, ALGORITHM));
      mac.update(salt);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }
}
