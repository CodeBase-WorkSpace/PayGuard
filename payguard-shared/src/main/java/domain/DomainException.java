package domain;

import java.io.Serial;
import java.net.URI;
import java.util.Locale;

/**
 * Base class for all domain-specific Organization rule violations.
 *
 * <p>Use this when the domain model wants to enforce an invariant or Organization rule (e.g.,
 * "Cannot publish a post that is already published"). These exceptions are thrown directly from
 * inside Entities/Aggregates.
 *
 * <p>Extending RuntimeException makes them unchecked so they can bubble up to the Application layer
 * for proper translation to user-friendly responses. This keeps the domain model clean and focused
 * only on Organization rules.
 *
 * @see AggregateRoot
 * @since 1.0
 */
public abstract class DomainException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  protected DomainException() {
    super();
  }

  protected DomainException(String message) {
    super(message);
  }

  protected DomainException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Unique business error identifier (e.g., "CHAT_ROOM_NOT_FOUND"). */
  public abstract String getErrorCode();

  /** Human-readable, short summary of the error type. */
  public abstract String getTitle();

  /** Standard HTTP status code (expressed as a primitive int to avoid Web layer dependencies). */
  public abstract int getHttpStatus();

  /** Absolute URI identifying the error type for API documentation. */
  public URI getTypeUri() {
    final String BASE_ERROR_URI = "https://api.payguard.com/errors/";
    return URI.create(BASE_ERROR_URI + getErrorCode().toLowerCase(Locale.ROOT).replace("_", "-"));
  }
}
