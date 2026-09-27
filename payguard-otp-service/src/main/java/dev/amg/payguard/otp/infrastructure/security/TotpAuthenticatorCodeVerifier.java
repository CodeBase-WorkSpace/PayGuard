package dev.amg.payguard.otp.infrastructure.security;

import dev.amg.payguard.otp.domain.AuthenticatorCodeVerifier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class TotpAuthenticatorCodeVerifier implements AuthenticatorCodeVerifier {

  private static final int DIGITS = 6;
  private static final long STEP_SECONDS = 30;
  private final AuthenticatorSecretPort secrets;

  public TotpAuthenticatorCodeVerifier(AuthenticatorSecretPort secrets) {
    this.secrets = secrets;
  }

  @Override
  public boolean verify(String userId, String code, Instant at) {
    if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
      return false;
    }
    Optional<String> secret = secrets.secretFor(userId);
    if (secret.isEmpty()) {
      return false;
    }
    long counter = at.getEpochSecond() / STEP_SECONDS;
    for (long offset = -1; offset <= 1; offset++) {
      if (constantTimeEquals(code, generate(secret.get(), counter + offset))) {
        return true;
      }
    }
    return false;
  }

  private String generate(String encodedSecret, long counter) {
    try {
      byte[] secret = decodeBase32(encodedSecret);
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
      int offset = digest[digest.length - 1] & 0x0f;
      int binary =
          ((digest[offset] & 0x7f) << 24)
              | ((digest[offset + 1] & 0xff) << 16)
              | ((digest[offset + 2] & 0xff) << 8)
              | (digest[offset + 3] & 0xff);
      return "%06d".formatted(binary % 1_000_000);
    } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
      return "";
    }
  }

  private static byte[] decodeBase32(String input) {
    String normalized = input.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
    StringBuilder bits = new StringBuilder();
    for (char character : normalized.toCharArray()) {
      int value = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);
      if (value < 0) {
        throw new IllegalArgumentException("Invalid base32 secret");
      }
      bits.append(String.format("%5s", Integer.toBinaryString(value)).replace(' ', '0'));
    }
    byte[] decoded = new byte[bits.length() / 8];
    for (int index = 0; index < decoded.length; index++) {
      decoded[index] = (byte) Integer.parseInt(bits.substring(index * 8, index * 8 + 8), 2);
    }
    return decoded;
  }

  private static boolean constantTimeEquals(String left, String right) {
    return java.security.MessageDigest.isEqual(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }
}
