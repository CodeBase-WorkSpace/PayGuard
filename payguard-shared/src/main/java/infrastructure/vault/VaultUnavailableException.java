package infrastructure.vault;

/** A dependency failure whose message deliberately excludes secret-bearing responses. */
public final class VaultUnavailableException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public VaultUnavailableException() {
    super("Required secret service is unavailable or access is denied");
  }

  public VaultUnavailableException(Throwable failure) {
    super("Required secret service is unavailable or access is denied", sanitizedCause(failure));
  }

  /**
   * Keep diagnostic stack frames while discarding messages and nested credential-bearing causes.
   */
  static Throwable sanitizedCause(Throwable failure) {
    var sanitized = new IllegalStateException("Dependency details redacted");
    sanitized.setStackTrace(failure.getStackTrace());
    return sanitized;
  }
}
