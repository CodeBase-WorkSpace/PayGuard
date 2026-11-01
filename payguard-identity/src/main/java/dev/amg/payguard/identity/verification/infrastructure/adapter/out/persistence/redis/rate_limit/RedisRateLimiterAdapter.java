package dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.redis.rate_limit;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitDecision;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPolicy;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPort;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitUnavailableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiterAdapter implements RateLimitPort {
  private static final DefaultRedisScript<List> ACQUIRE_SCRIPT = new DefaultRedisScript<>();

  static {
    ACQUIRE_SCRIPT.setLocation(new ClassPathResource("redis/scripts/token_bucket.lua"));
    ACQUIRE_SCRIPT.setResultType(List.class);
  }

  private final StringRedisTemplate redis;
  private final int maxIssues;
  private final int maxVerifications;
  private final Duration window;

  public RedisRateLimiterAdapter(
      StringRedisTemplate redis,
      @Value("${identity.rate-limit.max-issues:3}") int maxIssues,
      @Value("${identity.rate-limit.max-verifications:20}") int maxVerifications,
      @Value("${identity.rate-limit.window:PT15M}") Duration window) {
    this.redis = Objects.requireNonNull(redis, "redis");
    Objects.requireNonNull(window, "window");
    if (maxIssues < 1 || maxVerifications < 1 || window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("OTP rate limits and window must be positive");
    }
    try {
      if (window.toMillis() <= 0) {
        throw new IllegalArgumentException(
            "OTP rate-limit window must be at least one millisecond");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("OTP rate-limit window is too large", exception);
    }
    this.maxIssues = maxIssues;
    this.maxVerifications = maxVerifications;
    this.window = window;
  }

  @Override
  public RateLimitDecision acquire(RateLimitRequest request) {
    int maximum =
        switch (request.operation()) {
          case ISSUE, RESEND -> maxIssues;
          case VERIFY -> maxVerifications;
        };
    String prefix = "identity:rate:" + request.operation().name().toLowerCase(Locale.ROOT);
    List<String> keys = new ArrayList<>();
    List<String> arguments = new ArrayList<>();
    addBucket(keys, arguments, prefix + ":ip:" + safe(request.ipAddress()), "ip", maximum);
    if (request.customerId() != null) {
      addBucket(
          keys,
          arguments,
          prefix + ":customer:" + request.purpose() + ":" + safe(request.customerId()),
          "customer",
          maximum);
    }
    if (request.recipientFingerprint() != null) {
      addBucket(
          keys,
          arguments,
          prefix + ":recipient:" + request.purpose() + ":" + safe(request.recipientFingerprint()),
          "recipient",
          maximum);
    }
    try {
      List<?> result = redis.execute(ACQUIRE_SCRIPT, keys, arguments.toArray());
      Objects.requireNonNull(result, "result cannot be null");
      if (result.size() != 3
          || !(result.get(0) instanceof Long admitted)
          || !(result.get(1) instanceof Long retryMillis)
          || !(result.get(2) instanceof String policyName)) {
        throw new RateLimitUnavailableException("Redis did not return a valid rate-limit decision");
      }
      if (admitted == 1 && retryMillis == 0 && policyName.isEmpty()) {
        return RateLimitDecision.allowed();
      }
      if (admitted == 0 && retryMillis > 0 && !policyName.isBlank()) {
        return RateLimitDecision.rejected(Duration.ofMillis(retryMillis), policyName);
      }
      throw new RateLimitUnavailableException("Redis returned an invalid rate-limit decision");
    } catch (DataAccessException exception) {
      throw new RateLimitUnavailableException("OTP rate limiter is unavailable", exception);
    }
  }

  private void addBucket(
      List<String> keys, List<String> arguments, String key, String name, int maximum) {
    RateLimitPolicy policy = new RateLimitPolicy(name, maximum, maximum, window);
    keys.add(key);
    arguments.add(policy.name());
    arguments.add(Long.toString(policy.capacity()));
    arguments.add(Long.toString(policy.refillTokens()));
    arguments.add(Long.toString(policy.refillPeriod().toMillis()));
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
