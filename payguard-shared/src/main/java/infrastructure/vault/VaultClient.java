package infrastructure.vault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Scoped AppRole client with TLS verification, bounded calls and sanitized failures. Authentication
 * material lives in provisioned files. A missing KV item is distinct from denial or an outage.
 */
public final class VaultClient implements AutoCloseable {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(VaultClient.class);
  private static final String POST = "POST";
  private final URI address;
  private final Path roleIdFile;
  private final Path secretIdFile;
  private final HttpClient http;
  private final JsonMapper json = JsonMapper.builder().build();
  private String token = "";
  private Instant renewAt = Instant.EPOCH;
  private Instant expiresAt = Instant.EPOCH;

  public VaultClient(URI address, Path caFile, Path roleIdFile, Path secretIdFile) {
    if (!"https".equals(address.getScheme()) || address.getUserInfo() != null) {
      throw new IllegalArgumentException("Vault requires an HTTPS endpoint without credentials");
    }
    this.address = address;
    this.roleIdFile = roleIdFile;
    this.secretIdFile = secretIdFile;
    try {
      KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
      trust.load(null);
      try (var input = Files.newInputStream(caFile)) {
        int index = 0;
        for (var certificate :
            CertificateFactory.getInstance("X.509").generateCertificates(input)) {
          trust.setCertificateEntry("ca-" + index, certificate);
          index++;
        }
      }
      TrustManagerFactory managers =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      managers.init(trust);
      SSLContext tls = SSLContext.getInstance("TLS");
      tls.init(null, managers.getTrustManagers(), null);
      http = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(3)).build();
    } catch (Exception exception) {
      throw new VaultUnavailableException(exception);
    }
  }

  public JsonNode read(String path) {
    return request("GET", path, null, false).orElseThrow(VaultUnavailableException::new);
  }

  public Optional<JsonNode> readOptional(String path) {
    return request("GET", path, null, true);
  }

  public JsonNode write(String path, Map<String, ?> body) {
    return request(POST, path, body, false).orElseThrow(VaultUnavailableException::new);
  }

  private synchronized Optional<JsonNode> request(
      String method, String path, @Nullable Map<String, ?> body, boolean missingAllowed) {
    authenticate();
    return exchange(method, path, body, token, missingAllowed);
  }

  private void authenticate() {
    Instant now = Instant.now();
    if (!token.isEmpty() && now.isBefore(renewAt)) {
      return;
    }
    if (!token.isEmpty() && now.isBefore(expiresAt)) {
      try {
        acceptAuthentication(
            exchange(POST, "auth/token/renew-self", Map.of(), token, false).orElseThrow());
        return;
      } catch (VaultUnavailableException exception) {
        /* A still-valid token can be used briefly; expired tokens require reauthentication. */
        renewAt = now.plusSeconds(1);
        return;
      }
    }
    try {
      acceptAuthentication(
          exchange(
                  POST,
                  "auth/approle/login",
                  Map.of(
                      "role_id", Files.readString(roleIdFile).strip(),
                      "secret_id", Files.readString(secretIdFile).strip()),
                  "",
                  false)
              .orElseThrow());
    } catch (IOException exception) {
      throw new VaultUnavailableException(exception);
    }
  }

  private void acceptAuthentication(JsonNode response) {
    JsonNode auth = response.path("auth");
    String nextToken = auth.path("client_token").asText();
    long ttl = auth.path("lease_duration").asLong();
    if (nextToken.isEmpty() || ttl < 2 || auth.path("policies").toString().contains("\"root\"")) {
      throw new VaultUnavailableException();
    }
    token = nextToken;
    expiresAt = Instant.now().plusSeconds(ttl);
    renewAt = Instant.now().plusSeconds(Math.max(1, ttl / 2));
  }

  private Optional<JsonNode> exchange(
      String method,
      String path,
      @Nullable Map<String, ?> body,
      String authentication,
      boolean missingAllowed) {
    if (!path.matches("[A-Za-z0-9_./-]+") || path.contains("..") || path.startsWith("/")) {
      throw new IllegalArgumentException("Invalid secret path");
    }
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(address.resolve("/v1/" + path))
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json");
      if (!authentication.isEmpty()) {
        request.header("X-Vault-Token", authentication);
      }
      request.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 404 && missingAllowed) {
        JsonNode missing = json.readTree(response.body());
        JsonNode errors = missing.path("errors");
        boolean absentKv =
            (errors.isArray() && errors.isEmpty())
                || missing.path("data").path("metadata").path("version").asInt() > 0;
        if (path.startsWith("secret/data/") && absentKv) return Optional.empty();
        throw new VaultUnavailableException();
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        if ((response.statusCode() == 401 || response.statusCode() == 403)
            && authentication.equals(token)) {
          token = "";
          renewAt = Instant.EPOCH;
          expiresAt = Instant.EPOCH;
        }
        throw new VaultUnavailableException();
      }
      return Optional.of(
          response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body()));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new VaultUnavailableException(exception);
    } catch (IOException | tools.jackson.core.JacksonException exception) {
      throw new VaultUnavailableException(exception);
    }
  }

  @Override
  public synchronized void close() {
    if (!token.isEmpty()) {
      try {
        exchange(POST, "auth/token/revoke-self", Map.of(), token, false);
      } catch (VaultUnavailableException exception) {
        /* Vault expires unreachable tokens and their remaining leases independently. */
        LOG.warn("Vault token cleanup deferred to its expiry");
      }
    }
    http.close();
    token = "";
  }
}
