package domain;

/** Identifies a domain value object and defines its value-based equality contract. */
@FunctionalInterface
public interface ValueObject {
  /**
   * Returns whether this value object represents the same value as {@code other}.
   *
   * <p>Implementations should compare all components that define the value object and should not
   * compare object identity.
   */
  boolean sameValueAs(ValueObject other);
}
