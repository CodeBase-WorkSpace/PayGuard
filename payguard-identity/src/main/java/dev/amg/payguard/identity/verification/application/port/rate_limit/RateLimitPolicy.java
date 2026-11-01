package dev.amg.payguard.identity.verification.application.port.rate_limit;

import java.time.Duration;
import org.jspecify.annotations.NonNull;

/**
 * Defines an immutable rate-limiting policy for the Token Bucket algorithm.
 *
 * <p>A policy specifies the maximum number of tokens a bucket can hold and the rate at which tokens
 * are replenished.
 *
 * <p>All instances are guaranteed to satisfy the following invariants:
 *
 * <ul>
 *   <li>The policy name is neither null nor blank.
 *   <li>The bucket capacity is strictly positive.
 *   <li>The refill token count is strictly positive.
 *   <li>The refill period is strictly positive.
 *   <li>The refill period is representable in milliseconds.
 * </ul>
 *
 * <p>This record is independent of Redis and contains no mutable Token Bucket state.
 *
 * @param name unique identifier of the rate-limiting policy
 * @param capacity maximum number of tokens the bucket can hold
 * @param refillTokens number of tokens replenished per refill period
 * @param refillPeriod duration over which refillTokens are replenished
 */
public record RateLimitPolicy(
    @NonNull String name, long capacity, long refillTokens, @NonNull Duration refillPeriod) {

  /**
   * Validates the rate-limiting policy invariants.
   *
   * @throws IllegalArgumentException if any policy parameter is invalid
   */
  public RateLimitPolicy {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Rate limit policy name must not be null or blank");
    }

    if (capacity <= 0) {
      throw new IllegalArgumentException("Rate limit capacity must be greater than zero");
    }

    if (refillTokens <= 0) {
      throw new IllegalArgumentException("Refill tokens must be greater than zero");
    }

    if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
      throw new IllegalArgumentException("Refill period must be greater than zero");
    }

    try {
      if (refillPeriod.toMillis() <= 0) {
        throw new IllegalArgumentException("Refill period must be at least one millisecond");
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("Refill period exceeds the supported range", exception);
    }
  }
}
