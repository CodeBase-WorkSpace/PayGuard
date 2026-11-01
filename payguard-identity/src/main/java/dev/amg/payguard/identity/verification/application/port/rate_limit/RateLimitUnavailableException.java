package dev.amg.payguard.identity.verification.application.port.rate_limit;

/**
 * Indicates that the rate-limiting infrastructure could not reliably determine whether an operation
 * should be permitted.
 *
 * <p>This exception does not represent a rate-limit rejection. Callers must apply the configured
 * failure policy rather than automatically permitting the operation.
 */
public final class RateLimitUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public RateLimitUnavailableException(String message) {
    super(message);
  }

  /**
   * Creates an infrastructure failure with its underlying cause.
   *
   * @param message description of the failure
   * @param cause underlying infrastructure exception
   */
  public RateLimitUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
