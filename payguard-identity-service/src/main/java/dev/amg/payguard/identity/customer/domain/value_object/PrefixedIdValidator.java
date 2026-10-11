package dev.amg.payguard.identity.customer.domain.value_object;

import java.util.Objects;

final class PrefixedIdValidator {
  private static final int RANDOM_SUFFIX_LENGTH = 22;
  private static final String BASE62_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private PrefixedIdValidator() {}

  static String validateAndReturn(String identifier, String expectedPrefix) {
    Objects.requireNonNull(identifier);
    Objects.requireNonNull(expectedPrefix);

    if (identifier.length() != (expectedPrefix.length() + RANDOM_SUFFIX_LENGTH)
        || !identifier.startsWith(expectedPrefix)) {
      throw new IllegalArgumentException(
          "invalid identifier format: expected "
              + expectedPrefix
              + " followed by 22 Base62 characters");
    }

    for (int index = expectedPrefix.length(); index < identifier.length(); index++) {
      char character = identifier.charAt(index);
      boolean isBase62Character = BASE62_ALPHABET.indexOf(character) >= 0;
      if (!isBase62Character) {
        throw new IllegalArgumentException("invalid identifier suffix: expected Base62 characters");
      }
    }
    return identifier;
  }
}
