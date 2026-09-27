package domain;

/** Raised when a domain value or command contains invalid input. */
public final class InvalidDomainArgumentException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidDomainArgumentException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INVALID_DOMAIN_ARGUMENT";
  }

  @Override
  public String getTitle() {
    return "Invalid domain argument";
  }

  @Override
  public int getHttpStatus() {
    return 400;
  }
}
