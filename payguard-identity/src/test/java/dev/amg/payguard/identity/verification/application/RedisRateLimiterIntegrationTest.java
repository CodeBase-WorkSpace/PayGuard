package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitOperation;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitRequest;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.redis.rate_limit.RedisRateLimiterAdapter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRateLimiterIntegrationTest {
  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:8.0.3-alpine")).withExposedPorts(6379);

  private static LettuceConnectionFactory connection;
  private static StringRedisTemplate redis;

  @BeforeAll
  static void connect() {
    connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
    connection.afterPropertiesSet();
    redis = new StringRedisTemplate(connection);
    redis.afterPropertiesSet();
  }

  @AfterAll
  static void disconnect() {
    if (connection != null) {
      connection.destroy();
    }
  }

  @Test
  void adapterUsesTokenBucketScriptAndDoesNotConsumeOnRejection() {
    var limiter = new RedisRateLimiterAdapter(redis, 2, 2, Duration.ofMinutes(10));
    var request =
        new RateLimitRequest(
            RateLimitOperation.ISSUE, "customer-1", null, "192.0.2.10", OtpPurpose.LOGIN);
    String ipKey = "identity:rate:issue:ip:192.0.2.10";
    String customerKey = "identity:rate:issue:customer:LOGIN:customer-1";

    assertTrue(limiter.acquire(request).permitted());
    assertTrue(limiter.acquire(request).permitted());
    Map<Object, Object> ipBefore = redis.opsForHash().entries(ipKey);
    Map<Object, Object> customerBefore = redis.opsForHash().entries(customerKey);

    var rejected = limiter.acquire(request);

    assertFalse(rejected.permitted());
    assertEquals("ip", rejected.rejectedByPolicy());
    assertNotNull(rejected.retryAfter());
    assertTrue(!rejected.retryAfter().isZero() && !rejected.retryAfter().isNegative());
    assertEquals(ipBefore, redis.opsForHash().entries(ipKey));
    assertEquals(customerBefore, redis.opsForHash().entries(customerKey));
  }

  @Test
  void sharedIpLimitRejectsAnotherCustomerWithoutCreatingItsBucket() {
    var limiter = new RedisRateLimiterAdapter(redis, 1, 1, Duration.ofMinutes(10));
    var first =
        new RateLimitRequest(
            RateLimitOperation.VERIFY, "customer-1", null, "192.0.2.20", OtpPurpose.LOGIN);
    var second =
        new RateLimitRequest(
            RateLimitOperation.VERIFY, "customer-2", null, "192.0.2.20", OtpPurpose.LOGIN);

    assertTrue(limiter.acquire(first).permitted());
    assertFalse(limiter.acquire(second).permitted());
    assertEquals(Boolean.FALSE, redis.hasKey("identity:rate:verify:customer:LOGIN:customer-2"));
  }
}
