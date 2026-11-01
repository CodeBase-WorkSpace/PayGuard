package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitDecision;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitDecisionTest {

  @Test
  void shouldCreateAllowedDecision() {
    RateLimitDecision decision = RateLimitDecision.allowed();

    assertTrue(decision.permitted());
    assertNull(decision.retryAfter());
    assertNull(decision.rejectedByPolicy());
  }

  @Test
  void shouldCreateRejectedDecision() {
    Duration retryAfter = Duration.ofSeconds(30);

    RateLimitDecision decision = RateLimitDecision.rejected(retryAfter, "otp-issue");

    assertFalse(decision.permitted());
    assertEquals(retryAfter, decision.retryAfter());
    assertEquals("otp-issue", decision.rejectedByPolicy());
  }

  @Test
  void shouldRejectAllowedDecisionWithRetryDelay() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitDecision(true, Duration.ofSeconds(10), null));
  }

  @Test
  void shouldRejectAllowedDecisionWithPolicyName() {
    assertThrows(
        IllegalArgumentException.class, () -> new RateLimitDecision(true, null, "otp-issue"));
  }

  @Test
  void shouldRejectAllowedDecisionWithBothRejectionFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitDecision(true, Duration.ofSeconds(10), "otp-issue"));
  }

  @Test
  void shouldRejectMissingRetryDelay() {
    assertThrows(NullPointerException.class, () -> RateLimitDecision.rejected(null, "otp-issue"));
  }

  @Test
  void shouldRejectZeroRetryDelay() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RateLimitDecision.rejected(Duration.ZERO, "otp-issue"));
  }

  @Test
  void shouldRejectNegativeRetryDelay() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RateLimitDecision.rejected(Duration.ofSeconds(-1), "otp-issue"));
  }

  @Test
  void shouldRejectMissingPolicyName() {
    assertThrows(
        NullPointerException.class, () -> RateLimitDecision.rejected(Duration.ofSeconds(10), null));
  }

  @Test
  void shouldRejectEmptyPolicyName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RateLimitDecision.rejected(Duration.ofSeconds(10), ""));
  }

  @Test
  void shouldRejectWhitespaceOnlyPolicyName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> RateLimitDecision.rejected(Duration.ofSeconds(10), "   "));
  }

  @Test
  void shouldPreserveEqualityForAllowedDecisions() {
    RateLimitDecision first = RateLimitDecision.allowed();
    RateLimitDecision second = RateLimitDecision.allowed();

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void shouldPreserveEqualityForRejectedDecisions() {
    RateLimitDecision first = RateLimitDecision.rejected(Duration.ofSeconds(10), "otp-verify");

    RateLimitDecision second = RateLimitDecision.rejected(Duration.ofSeconds(10), "otp-verify");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void shouldDistinguishDifferentRejectionPolicies() {
    RateLimitDecision first = RateLimitDecision.rejected(Duration.ofSeconds(10), "otp-issue");

    RateLimitDecision second = RateLimitDecision.rejected(Duration.ofSeconds(10), "otp-verify");

    assertNotEquals(first, second);
  }

  @Test
  void shouldDistinguishDifferentRetryDelays() {
    RateLimitDecision first = RateLimitDecision.rejected(Duration.ofSeconds(10), "otp-issue");

    RateLimitDecision second = RateLimitDecision.rejected(Duration.ofSeconds(20), "otp-issue");

    assertNotEquals(first, second);
  }
}
