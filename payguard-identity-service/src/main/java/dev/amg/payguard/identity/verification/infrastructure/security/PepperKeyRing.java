package dev.amg.payguard.identity.verification.infrastructure.security;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Named HMAC keys; a legacy key must be explicitly selected for unversioned stored hashes. */
public record PepperKeyRing(String current, Map<String, String> keys, @Nullable String legacy) {
  public PepperKeyRing {
    keys = Map.copyOf(keys);
    if (!keys.containsKey(current)
        || (legacy != null && !keys.containsKey(legacy))
        || keys.entrySet().stream()
            .anyMatch(
                entry ->
                    !entry.getKey().matches("[A-Za-z0-9_-]{1,32}")
                        || entry.getValue().length() < 32)) {
      throw new IllegalArgumentException("Invalid HMAC key configuration");
    }
  }

  @Override
  public String toString() {
    return "PepperKeyRing[REDACTED]";
  }
}
