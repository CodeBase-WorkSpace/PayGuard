package dev.amg.payguard.identity.verification.infrastructure.security;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import infrastructure.vault.VaultClient;
import infrastructure.vault.VaultUnavailableException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Real scoped KV reads and key compatibility against the isolated TLS rehearsal Vault. */
class VaultSecretsIT {
  private static final Path LOCAL = Path.of("/workspace/.local/payguard");
  private static final String KEYS = "secret/data/local/identity/otp-keys";
  private static final String FIRST = "synthetic-first-pepper-marker-123456789";
  private static final String SECOND = "synthetic-second-pepper-marker-123456789";

  @Test
  void provisionedSeedIsCustomerScopedAndMissingIsDistinctFromDenied() {
    assertEquals("true", System.getenv("PAYGUARD_REHEARSAL"));
    CustomerId customer =
        CustomerId.parse(
            "cus_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 22));
    String path = "secret/data/local/identity/authenticators/" + customer.getValue();
    try (VaultClient operator = client("operator/approle");
        VaultClient identity = client("apps/identity");
        VaultClient wallet = client("apps/wallet")) {
      operator.write(path, Map.of("data", Map.of("seed", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")));
      try {
        var adapter = new VaultAuthenticatorSecretAdapter(identity, "local/identity");
        assertTrue(adapter.secretFor(customer).isPresent());
        assertTrue(adapter.secretFor(CustomerId.parse("cus_" + "R".repeat(22))).isEmpty());
        var totp = new TotpAuthenticatorCodeVerifier(adapter);
        assertTrue(totp.verify(customer, "287082", Instant.ofEpochSecond(59)));
        assertFalse(totp.verify(customer, "287083", Instant.ofEpochSecond(59)));
        var denied = new VaultAuthenticatorSecretAdapter(wallet, "local/identity");
        var failure =
            assertThrows(VaultUnavailableException.class, () -> denied.secretFor(customer));
        assertFalse(failure.getMessage().contains("GEZDGNBV"));
      } finally {
        operator.write(
            "secret/destroy/local/identity/authenticators/" + customer.getValue(),
            Map.of("versions", new int[] {1}));
      }
    }
  }

  @Test
  void actualKvRotationRetainsActiveHashesRejectsEarlyRetirementAndEventuallyRetiresOldKey() {
    try (VaultClient operator = client("operator/approle");
        VaultClient identity = client("apps/identity")) {
      var original = operator.read(KEYS).path("data").path("data");
      operator.write(
          KEYS,
          Map.of(
              "data",
              Map.of("current", "probeA", "keys", Map.of("probeA", FIRST), "legacy", "probeA")));
      try (var keys =
          new VaultPepperKeys(
              identity, Duration.ofSeconds(1), Duration.ofSeconds(1), "local/identity")) {
        var hasher = new HmacOtpCodeHasher(keys);
        String old = hasher.hash("synthetic-code");
        String legacy = old.substring(old.indexOf(':') + 1);
        assertTrue(hasher.matches("synthetic-code", legacy));
        operator.write(
            KEYS,
            Map.of(
                "data",
                Map.of(
                    "current",
                    "probeB",
                    "keys",
                    Map.of("probeA", FIRST, "probeB", SECOND),
                    "legacy",
                    "probeA")));
        await().atMost(Duration.ofSeconds(8)).until(() -> keys.get().current().equals("probeB"));
        String newer = hasher.hash("synthetic-code");
        assertTrue(newer.startsWith("probeB:"));
        assertTrue(hasher.matches("synthetic-code", old));
        assertTrue(hasher.matches("synthetic-code", legacy));
        operator.write(
            KEYS,
            Map.of(
                "data",
                Map.of(
                    "current",
                    "probeB",
                    "keys",
                    Map.of("probeA", FIRST, "probeB", SECOND),
                    "legacy",
                    "probeB")));
        await().atMost(Duration.ofSeconds(8)).until(() -> !keys.refreshHealthy());
        assertTrue(hasher.matches("synthetic-code", legacy));
        operator.write(
            KEYS,
            Map.of(
                "data",
                Map.of("current", "probeB", "keys", Map.of("probeA", FIRST, "probeB", SECOND))));
        Instant removal = Instant.now();
        await()
            .atMost(Duration.ofSeconds(8))
            .until(() -> Instant.now().isAfter(removal.plusSeconds(6)));
        assertTrue(hasher.matches("synthetic-code", old));
        assertTrue(hasher.matches("synthetic-code", legacy));
        operator.write(
            KEYS, Map.of("data", Map.of("current", "probeB", "keys", Map.of("probeB", SECOND))));
        await()
            .atMost(Duration.ofSeconds(14))
            .until(() -> !keys.get().keys().containsKey("probeA"));
        assertFalse(hasher.matches("synthetic-code", old));
        assertFalse(hasher.matches("synthetic-code", legacy));
        assertTrue(hasher.matches("synthetic-code", newer));
        assertFalse(keys.get().toString().contains(SECOND));
      } finally {
        operator.write(KEYS, Map.of("data", original));
      }
    }
  }

  @Test
  void invalidMissingKeyConfigurationFailsStartupWithoutDefault() {
    try (VaultClient wallet = client("apps/wallet")) {
      assertThrows(
          VaultUnavailableException.class,
          () ->
              new VaultPepperKeys(
                  wallet, Duration.ofMinutes(5), Duration.ofSeconds(30), "local/identity"));
    }
  }

  private static VaultClient client(String folder) {
    Path scope = LOCAL.resolve(folder);
    return new VaultClient(
        URI.create("https://vault:8200"),
        LOCAL.resolve("ca.crt"),
        scope.resolve("role-id"),
        scope.resolve("secret-id"));
  }
}
