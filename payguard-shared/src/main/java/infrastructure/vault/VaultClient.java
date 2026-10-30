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
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application KV client using a token delivered and renewed exclusively by Vault Agent. This Java
 * client never reads AppRole bootstrap material or manages Vault leases. A missing KV item is
 * distinct from denial or an outage.
 */
public final class VaultClient implements AutoCloseable {
  private static final String POST = "POST";
  private final URI address;
  private final Path tokenFile;
  private final HttpClient http;
  private final JsonMapper json = JsonMapper.builder().build();

  public VaultClient(URI address, Path caFile, Path tokenFile) {
    this(address, tokenFile, verifiedHttpClient(caFile));
  }

  VaultClient(URI address, Path tokenFile, HttpClient http) {
    if (!"https".equals(address.getScheme()) || address.getUserInfo() != null) {
      throw new IllegalArgumentException("Vault requires an HTTPS endpoint without credentials");
    }
    this.address = address;
    this.tokenFile = tokenFile;
    this.http = http;
  }

  private static HttpClient verifiedHttpClient(Path caFile) {
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
      return HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(3)).build();
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

  @Override
  public void close() {
    // The supervising Agent owns token and lease lifecycle.
    http.close();
  }

  private Optional<JsonNode> request(
      String method, String path, @Nullable Map<String, ?> body, boolean missingAllowed) {
    try {
      // Reopen the file: Agent replaces sinks atomically during reauthentication.
      String token = Files.readString(tokenFile).strip();
      if (token.isBlank()) throw new VaultUnavailableException();
      return exchange(method, path, body, token, missingAllowed);
    } catch (IOException exception) {
      throw new VaultUnavailableException(exception);
    }
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
}
