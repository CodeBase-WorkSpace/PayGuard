package dev.amg.payguard.identity.verification.application.port.rate_limit;

/**
 * Defines the application contract for distributed rate-limit admission.
 *
 * <p>Implementations evaluate the rate-limiting policies applicable to an OTP operation and return
 * an admission decision.
 *
 * <p>Each applicable policy represents a separate rate-limit dimension, such as a global limit,
 * customer limit, or IP address limit.
 *
 * <p>A request is permitted only when all applicable policies allow it.
 *
 * <p>Implementations must enforce their documented concurrency guarantees. Infrastructure failures
 * must not be represented as permitted decisions.
 */
@FunctionalInterface
public interface RateLimitPort {

  /**
   * Evaluates whether an OTP operation may proceed.
   *
   * <p>Rate-limit state must be shared across service instances.
   *
   * <p>A permitted decision indicates that the relevant admission tokens have been consumed
   * successfully.
   *
   * <p>A rejected decision indicates that at least one applicable policy does not have sufficient
   * available tokens.
   *
   * @param request validated OTP operation and identity context
   * @return the admission decision
   * @throws RateLimitUnavailableException if admission cannot be evaluated reliably
   */
  RateLimitDecision acquire(RateLimitRequest request) throws RateLimitUnavailableException;
}
