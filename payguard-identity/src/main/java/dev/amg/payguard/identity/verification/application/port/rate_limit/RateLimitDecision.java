package dev.amg.payguard.identity.verification.application.port.rate_limit;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents the outcome of an OTP rate-limit admission check.
 *
 * @param permitted whether the request is permitted
 * @param retryAfter duration before retrying, or null if permitted
 * @param rejectedByPolicy rejecting policy name, or null if permitted
 */
public record RateLimitDecision(
    boolean permitted, @Nullable Duration retryAfter, @Nullable String rejectedByPolicy) {

  public RateLimitDecision {
    if (permitted) {
      if (retryAfter != null || rejectedByPolicy != null) {
        throw new IllegalArgumentException(
            "Permitted decisions must not contain rejection metadata");
      }
    } else {
      Objects.requireNonNull(retryAfter, "retryAfter");
      Objects.requireNonNull(rejectedByPolicy, "rejectedByPolicy");

      // Only happens if `permitted` is NULL.
      if (retryAfter.isZero() || retryAfter.isNegative()) {
        throw new IllegalArgumentException("Retry delay must be positive");
      }

      if (rejectedByPolicy.isBlank()) {
        throw new IllegalArgumentException("Rejected policy name must not be blank");
      }
    }
  }

  /**
   * Creates a decision that permits the request.
   *
   * @return a permitted rate-limit decision
   */
  public static RateLimitDecision allowed() {
    return new RateLimitDecision(true, null, null);
  }

  /**
   * Creates a decision that rejects the request.
   *
   * @param retryAfter positive duration before retrying
   * @param rejectedByPolicy non-blank rejecting policy name
   * @return a rejected rate-limit decision
   */
  public static RateLimitDecision rejected(Duration retryAfter, String rejectedByPolicy) {
    return new RateLimitDecision(false, retryAfter, rejectedByPolicy);
  }
}
