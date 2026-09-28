package domain;

/** Central validation helpers that prevent default JDK exceptions from escaping the domain. */
public final class DomainValidation {
  private DomainValidation() {}

  public static <T> T requireNonNull(T value, String fieldName) {
    if (value == null) {
      throw new InvalidDomainArgumentException(fieldName + " cannot be null");
    }
    return value;
  }

  public static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new InvalidDomainArgumentException(fieldName + " cannot be blank");
    }
    return value;
  }
}
