package dev.amg.payguard.otp.infrastructure.redis;

import dev.amg.payguard.otp.domain.OtpPurpose;
import dev.amg.payguard.otp.domain.StepUpTokenPort;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisStepUpTokenAdapter implements StepUpTokenPort {

  private static final int TOKEN_PARTS = 3;
  private final StringRedisTemplate redis;
  private final Duration ttl;
  private final SecureRandom random = new SecureRandom();

  public RedisStepUpTokenAdapter(
      StringRedisTemplate redis, @Value("${otp.step-up-token.ttl:PT3M}") Duration ttl) {
    this.redis = redis;
    this.ttl = ttl;
  }

  @Override
  public IssuedStepUpToken issue(String userId, OtpPurpose purpose, Instant now) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = now.plus(ttl);
    String value =
        Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(userId.getBytes(StandardCharsets.UTF_8))
            + "|"
            + purpose
            + "|"
            + expiresAt.toEpochMilli();
    redis.opsForValue().set("otp:step-up:" + token, value, ttl);
    return new IssuedStepUpToken(token, expiresAt);
  }

  @Override
  public boolean consume(String token, String userId, OtpPurpose purpose, Instant now) {
    if (token == null || token.isBlank()) {
      return false;
    }
    String value = redis.opsForValue().getAndDelete("otp:step-up:" + token);
    if (value == null) {
      return false;
    }
    String[] parts = value.split("\\|", -1);
    if (parts.length != TOKEN_PARTS) {
      return false;
    }
    try {
      String storedUser =
          new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
      return storedUser.equals(userId)
          && purpose.name().equals(parts[1])
          && now.isBefore(Instant.ofEpochMilli(Long.parseLong(parts[2])));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
