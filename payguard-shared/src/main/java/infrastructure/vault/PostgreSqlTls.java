package infrastructure.vault;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rejects JDBC parameter overrides that could bypass the selected certificate/credential policy.
 */
final class PostgreSqlTls {
  private static final String PREFIX = "jdbc:postgresql://";
  private static final int PARAMETER_PARTS = 2;
  private static final Set<String> FORBIDDEN =
      Set.of("user", "password", "sslfactory", "sslhostnameverifier");

  private PostgreSqlTls() {}

  static void requireVerified(String url) {
    if (!url.startsWith(PREFIX))
      throw new IllegalArgumentException("Verified PostgreSQL TLS required");
    URI address = URI.create(url.substring("jdbc:".length()));
    if (address.getHost() == null
        || address.getUserInfo() != null
        || address.getFragment() != null
        || address.getRawQuery() == null)
      throw new IllegalArgumentException("Invalid PostgreSQL endpoint");
    Map<String, String> parameters = new HashMap<>();
    for (String entry : address.getRawQuery().split("&")) {
      String[] pair = entry.split("=", 2);
      if (pair.length != PARAMETER_PARTS)
        throw new IllegalArgumentException("Invalid PostgreSQL parameter");
      String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
      String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
      if (FORBIDDEN.contains(name) || parameters.putIfAbsent(name, value) != null)
        throw new IllegalArgumentException("PostgreSQL credential/TLS override rejected");
    }
    if (!"verify-full".equals(parameters.get("sslmode"))
        || !parameters.getOrDefault("sslrootcert", "").startsWith("/"))
      throw new IllegalArgumentException("Verified PostgreSQL TLS and explicit CA required");
  }
}
