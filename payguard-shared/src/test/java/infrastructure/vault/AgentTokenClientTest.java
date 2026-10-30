package infrastructure.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests Agent token handoff using a transport fixture without sockets or JVM instrumentation. */
class AgentTokenClientTest {
  @TempDir Path directory;

  @Test
  void observesAtomicTokenReplacementAndNeverCallsAuthenticationEndpoints() throws Exception {
    Path token = directory.resolve("token");
    Files.writeString(token, "synthetic-first-token\n");
    var transport = new Transport(new Response(200, "{}"), new Response(200, "{}"));
    try (VaultClient client = new VaultClient(URI.create("https://vault:8200"), token, transport)) {
      client.read("secret/data/local/identity/otp-keys");
      Path replacement = directory.resolve("replacement");
      Files.writeString(replacement, "synthetic-next-token\n");
      Files.move(
          replacement, token, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      client.read("secret/data/local/identity/otp-keys");
    }
    assertEquals(2, transport.requests.size());
    assertEquals(
        "synthetic-first-token",
        transport.requests.get(0).headers().firstValue("X-Vault-Token").orElseThrow());
    assertEquals(
        "synthetic-next-token",
        transport.requests.get(1).headers().firstValue("X-Vault-Token").orElseThrow());
    assertTrue(
        transport.requests.stream()
            .allMatch(request -> request.uri().getPath().startsWith("/v1/secret/data/")));
    assertTrue(transport.requests.stream().allMatch(request -> request.method().equals("GET")));
    assertTrue(transport.closed);
  }

  @Test
  void distinguishesMissingKvFromPolicyDenialWithoutLeakingResponse() throws Exception {
    Path token = directory.resolve("token");
    Files.writeString(token, "synthetic-token");
    var transport =
        new Transport(
            new Response(404, "{\"errors\":[]}"), new Response(403, "secret-response-marker"));
    try (VaultClient client = new VaultClient(URI.create("https://vault:8200"), token, transport)) {
      assertTrue(
          client.readOptional("secret/data/local/identity/authenticators/cus_example").isEmpty());
      var failure =
          assertThrows(
              VaultUnavailableException.class,
              () -> client.readOptional("secret/data/local/identity/authenticators/cus_example"));
      assertFalse(failure.toString().contains("secret-response-marker"));
    }
  }

  @Test
  void missingOrEmptyAgentTokenFailsBeforeSendingARequest() throws Exception {
    var transport = new Transport();
    Path token = directory.resolve("token");
    try (VaultClient client = new VaultClient(URI.create("https://vault:8200"), token, transport)) {
      assertThrows(VaultUnavailableException.class, () -> client.read("auth/token/lookup-self"));
      Files.writeString(token, "\n");
      assertThrows(VaultUnavailableException.class, () -> client.read("auth/token/lookup-self"));
    }
    assertTrue(transport.requests.isEmpty());
  }

  private record Response(int statusCode, String body) implements HttpResponse<String> {
    public HttpRequest request() {
      return HttpRequest.newBuilder(uri()).build();
    }

    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    public HttpHeaders headers() {
      return HttpHeaders.of(Map.of(), (name, value) -> true);
    }

    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    public URI uri() {
      return URI.create("https://vault:8200");
    }

    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }

  private static final class Transport extends HttpClient {
    private final ArrayDeque<Response> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();
    private boolean closed;

    private Transport(Response... responses) {
      this.responses.addAll(List.of(responses));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      requests.add(request);
      return (HttpResponse<T>) responses.removeFirst();
    }

    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    public SSLContext sslContext() {
      throw new UnsupportedOperationException();
    }

    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    public Version version() {
      return Version.HTTP_1_1;
    }

    public Optional<Executor> executor() {
      return Optional.empty();
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      throw new UnsupportedOperationException();
    }

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> handler,
        HttpResponse.PushPromiseHandler<T> pushHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
