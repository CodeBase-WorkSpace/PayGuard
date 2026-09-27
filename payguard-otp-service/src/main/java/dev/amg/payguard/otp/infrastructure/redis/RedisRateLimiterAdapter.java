package dev.amg.payguard.otp.infrastructure.redis;

import dev.amg.payguard.otp.domain.OtpPurpose;
import dev.amg.payguard.otp.domain.RateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiterAdapter implements RateLimiter {

  private final StringRedisTemplate redis;
  private final int maxIssues;
  private final int maxVerifications;
  private final Duration window;

  public RedisRateLimiterAdapter(
      StringRedisTemplate redis,
      @Value("${otp.rate-limit.max-issues:3}") int maxIssues,
      @Value("${otp.rate-limit.max-verifications:20}") int maxVerifications,
      @Value("${otp.rate-limit.window:PT15M}") Duration window) {
    this.redis = redis;
    this.maxIssues = maxIssues;
    this.maxVerifications = maxVerifications;
    this.window = window;
  }

  @Override
  public boolean allowIssue(String userId, OtpPurpose purpose, String sourceIp) {
    return allow("issue", userId, purpose, sourceIp, maxIssues);
  }

  @Override
  public boolean allowVerify(String userId, OtpPurpose purpose, String sourceIp) {
    return allow("verify", userId, purpose, sourceIp, maxVerifications);
  }

  private boolean allow(
      String operation, String userId, OtpPurpose purpose, String sourceIp, int maximum) {
    String key =
        "otp:rate:" + operation + ":" + safe(userId) + ":" + purpose + ":" + safe(sourceIp);
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, window);
    }
    return count != null && count <= maximum;
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
