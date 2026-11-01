package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitOperation;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitUnavailableException;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.redis.rate_limit.RedisRateLimiterAdapter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisRateLimiterAdapterTest {
  private static final RateLimitRequest REQUEST =
      new RateLimitRequest(
          RateLimitOperation.ISSUE, "customer-1", null, "192.0.2.10", OtpPurpose.LOGIN);

  @Test
  void admitsWithinLimitAndScopesTheKeyToOtpPurpose() {
    assertTrue(new ClassPathResource("redis/scripts/token_bucket.lua").exists());
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    doAnswer(
            invocation -> {
              List<String> keys = invocation.getArgument(1);
              assertEquals(
                  List.of(
                      "identity:rate:issue:ip:192.0.2.10",
                      "identity:rate:issue:customer:LOGIN:customer-1"),
                  keys);
              return List.of(1L, 0L, "");
            })
        .when(redis)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertTrue(
        new RedisRateLimiterAdapter(redis, 1, 2, Duration.ofMinutes(15))
            .acquire(REQUEST)
            .permitted());
  }

  @Test
  void rejectsWithRemainingWindow() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    doAnswer(invocation -> List.of(0L, 30_000L, "ip"))
        .when(redis)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    var decision =
        new RedisRateLimiterAdapter(redis, 1, 2, Duration.ofMinutes(15)).acquire(REQUEST);

    assertFalse(decision.permitted());
    assertEquals(Duration.ofSeconds(30), decision.retryAfter());
    assertEquals("ip", decision.rejectedByPolicy());
  }

  @Test
  void failsClosedWhenRedisIsUnavailable() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    doThrow(new RedisConnectionFailureException("offline"))
        .when(redis)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    assertThrows(
        RateLimitUnavailableException.class,
        () -> new RedisRateLimiterAdapter(redis, 1, 2, Duration.ofMinutes(15)).acquire(REQUEST));
  }
}
