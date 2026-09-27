package domain;

/** Raised when an operation violates a domain state transition. */
public final class InvalidDomainStateException extends DomainException {

  private static final long serialVersionUID = 1L;

  public InvalidDomainStateException(String message) {
    super(message);
  }

  @Override
  public String getErrorCode() {
    return "INVALID_DOMAIN_STATE";
  }

  @Override
  public String getTitle() {
    return "Invalid domain state";
  }

  @Override
  public int getHttpStatus() {
    return 409;
  }
}
