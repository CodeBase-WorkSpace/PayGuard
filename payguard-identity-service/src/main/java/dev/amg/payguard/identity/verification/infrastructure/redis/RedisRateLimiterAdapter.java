package dev.amg.payguard.identity.verification.infrastructure.redis;

import dev.amg.payguard.identity.verification.application.port.VerificationQuota;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiterAdapter implements VerificationQuota {

  private final StringRedisTemplate redis;
  private final int maxIssues;
  private final int maxVerifications;
  private final Duration window;

  public RedisRateLimiterAdapter(
      StringRedisTemplate redis,
      @Value("${identity.rate-limit.max-issues:3}") int maxIssues,
      @Value("${identity.rate-limit.max-verifications:20}") int maxVerifications,
      @Value("${identity.rate-limit.window:PT15M}") Duration window) {
    this.redis = redis;
    this.maxIssues = maxIssues;
    this.maxVerifications = maxVerifications;
    this.window = window;
  }

  @Override
  public boolean tryAcquire(VerificationQuota.Request request) {
    boolean issuing = request.operation() == VerificationQuota.Operation.ISSUE;
    return allow(
        issuing ? "issue" : "verify",
        request.customerId().getValue(),
        request.purpose(),
        request.sourceIp(),
        issuing ? maxIssues : maxVerifications);
  }

  private boolean allow(
      String operation, String userId, OtpPurpose purpose, String sourceIp, int maximum) {
    String key =
        "identity:rate:" + operation + ":" + safe(userId) + ":" + purpose + ":" + safe(sourceIp);
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
