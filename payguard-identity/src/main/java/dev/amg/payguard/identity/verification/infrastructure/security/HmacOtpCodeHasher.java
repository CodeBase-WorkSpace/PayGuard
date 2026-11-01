package dev.amg.payguard.identity.verification.infrastructure.security;

import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacOtpCodeHasher implements OtpCodeHasher {

  private static final String ALGORITHM = "HmacSHA256";
  private static final int VERSIONED_PARTS = 3;
  private final SecureRandom secureRandom = new SecureRandom();
  private final Supplier<PepperKeyRing> keys;

  public HmacOtpCodeHasher(VaultPepperKeys keys) {
    this((Supplier<PepperKeyRing>) keys);
  }

  public HmacOtpCodeHasher(Supplier<PepperKeyRing> keys) {
    this.keys = keys;
  }

  /** Explicit synthetic/legacy wiring; production obtains a named key ring from Vault. */
  public HmacOtpCodeHasher(String pepper) {
    this(() -> new PepperKeyRing("test", Map.of("test", pepper), "test"));
  }

  @Override
  public String hash(String value) {
    byte[] salt = new byte[16];
    secureRandom.nextBytes(salt);
    PepperKeyRing ring = keys.get();
    return ring.current()
        + ":"
        + HexFormat.of().formatHex(salt)
        + ":"
        + HexFormat.of().formatHex(digest(value, salt, ring.keys().get(ring.current())));
  }

  @Override
  public boolean matches(String value, String encodedHash) {
    if (value == null || encodedHash == null) {
      return false;
    }
    try {
      PepperKeyRing ring = keys.get();
      String[] parts = encodedHash.split(":", -1);
      String keyId;
      int saltIndex;
      if (parts.length == VERSIONED_PARTS) {
        keyId = parts[0];
        saltIndex = 1;
      } else if (parts.length == 2 && ring.legacy() != null) {
        keyId = ring.legacy();
        saltIndex = 0;
      } else {
        return false;
      }
      String pepper = ring.keys().get(keyId);
      if (pepper == null) {
        return false;
      }
      byte[] salt = HexFormat.of().parseHex(parts[saltIndex]);
      byte[] expected = HexFormat.of().parseHex(parts[saltIndex + 1]);
      return salt.length == 16
          && expected.length == 32
          && MessageDigest.isEqual(expected, digest(value, salt, pepper));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private byte[] digest(String value, byte[] salt, String pepper) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      mac.update(salt);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }
}
