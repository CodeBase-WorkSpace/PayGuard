package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPolicy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPolicyTest {

  @Test
  void shouldCreateValidPolicy() {
    RateLimitPolicy policy = new RateLimitPolicy("otp-issue", 100, 25, Duration.ofSeconds(1));

    assertEquals("otp-issue", policy.name());
    assertEquals(100, policy.capacity());
    assertEquals(25, policy.refillTokens());
    assertEquals(Duration.ofSeconds(1), policy.refillPeriod());
  }

  @Test
  void shouldRejectNullName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy(null, 100, 25, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectBlankName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("   ", 100, 25, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectZeroCapacity() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 0, 25, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectNegativeCapacity() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", -1, 25, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectZeroRefillTokens() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 100, 0, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectNegativeRefillTokens() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 100, -1, Duration.ofSeconds(1)));
  }

  @Test
  void shouldRejectNullRefillPeriod() {
    assertThrows(IllegalArgumentException.class, () -> new RateLimitPolicy("otp", 100, 25, null));
  }

  @Test
  void shouldRejectZeroRefillPeriod() {
    assertThrows(
        IllegalArgumentException.class, () -> new RateLimitPolicy("otp", 100, 25, Duration.ZERO));
  }

  @Test
  void shouldRejectNegativeRefillPeriod() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 100, 25, Duration.ofSeconds(-1)));
  }

  @Test
  void shouldRejectSubMillisecondRefillPeriod() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 100, 25, Duration.ofNanos(1)));
  }

  @Test
  void shouldRejectRefillPeriodExceedingLongMilliseconds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitPolicy("otp", 100, 25, Duration.ofSeconds(Long.MAX_VALUE)));
  }

  @Test
  void shouldAcceptMinimumSupportedRefillPeriod() {
    RateLimitPolicy policy = new RateLimitPolicy("otp", 100, 25, Duration.ofMillis(1));

    assertEquals(Duration.ofMillis(1), policy.refillPeriod());
  }

  @Test
  void shouldPreserveRecordValueEquality() {
    RateLimitPolicy first = new RateLimitPolicy("otp", 100, 25, Duration.ofSeconds(1));

    RateLimitPolicy second = new RateLimitPolicy("otp", 100, 25, Duration.ofSeconds(1));

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}
