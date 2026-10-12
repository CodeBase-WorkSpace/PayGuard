package infrastructure.vault;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs only through the explicit local/CI Vault runner against provisioned, isolated TLS engines.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultDatabaseIT {
  private static final Path LOCAL = Path.of("/workspace/.local/payguard");
  private static final String PROBE =
      "vault_probe_" + java.util.UUID.randomUUID().toString().replace("-", "");
  private static final String ROLE = "local-identity-runtime";
  private static VaultClient operator;
  private static JsonNode originalRole;

  @BeforeAll
  static void prepare() throws Exception {
    assertEquals("true", System.getenv("PAYGUARD_REHEARSAL"));
    operator = client(LOCAL.resolve("operator/approle"));
    originalRole = operator.read("database/roles/" + ROLE).path("data");
    operator.write(
        "database/roles/" + ROLE,
        Map.of(
            "db_name",
            "local-identity",
            "creation_statements",
            originalRole.path("creation_statements"),
            "revocation_statements",
            originalRole.path("revocation_statements"),
            "renew_statements",
            originalRole.path("renew_statements"),
            "default_ttl",
            "30s",
            "max_ttl",
            "45s"));
    try (Connection connection = migration("identity");
        var statement = connection.createStatement()) {
      statement.execute(
          "CREATE TABLE payguard."
              + PROBE
              + " (id integer PRIMARY KEY, marker varchar(64) NOT NULL)");
    }
  }

  @AfterAll
  static void restore() throws Exception {
    if (operator != null) {
      try {
        if (originalRole != null) {
          operator.write(
              "database/roles/" + ROLE,
              Map.of(
                  "db_name",
                  "local-identity",
                  "creation_statements",
                  originalRole.path("creation_statements"),
                  "revocation_statements",
                  originalRole.path("revocation_statements"),
                  "renew_statements",
                  originalRole.path("renew_statements"),
                  "default_ttl",
                  "1h",
                  "max_ttl",
                  "4h"));
        }
        try (Connection connection = migration("identity");
            var statement = connection.createStatement()) {
          statement.execute("DROP TABLE IF EXISTS payguard." + PROBE);
        }
      } finally {
        operator.close();
      }
    }
  }

  @Test
  @Order(1)
  void independentlyScopedRuntimeRolesAreUsableAndDeniedDdlAndOtherServicePaths() throws Exception {
    for (String service : new String[] {"identity", "wallet", "loan", "collateral"}) {
      try (VaultClient vault = client(LOCAL.resolve("apps/" + service))) {
        try (Connection owner = migration(service);
            var statement = owner.createStatement()) {
          statement.execute("CREATE TABLE payguard." + PROBE + "_dml(id integer PRIMARY KEY)");
        }
        var credential = vault.read("database/creds/local-" + service + "-runtime");
        try (Connection connection = login(service, credential);
            var statement = connection.createStatement()) {
          assertTrue(connection.isValid(1));
          try (var rows = statement.executeQuery("SHOW statement_timeout")) {
            assertTrue(rows.next());
            assertEquals("10s", rows.getString(1));
          }
          assertEquals(
              1, statement.executeUpdate("INSERT INTO payguard." + PROBE + "_dml VALUES (1)"));
          assertEquals(
              1, statement.executeUpdate("UPDATE payguard." + PROBE + "_dml SET id=2 WHERE id=1"));
          try (var rows = statement.executeQuery("SELECT id FROM payguard." + PROBE + "_dml")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
          }
          assertEquals(
              1, statement.executeUpdate("DELETE FROM payguard." + PROBE + "_dml WHERE id=2"));
          assertThrows(
              SQLException.class,
              () -> statement.execute("CREATE TABLE payguard.unauthorized_probe(id integer)"));
          assertThrows(
              SQLException.class,
              () -> statement.execute("CREATE TABLE public.unauthorized_probe(id integer)"));
          assertThrows(
              SQLException.class,
              () -> statement.executeQuery("SELECT rolpassword FROM pg_authid"));
        } finally {
          try (Connection owner = migration(service);
              var statement = owner.createStatement()) {
            statement.execute("DROP TABLE payguard." + PROBE + "_dml");
          }
        }
        String other = service.equals("identity") ? "wallet" : "identity";
        assertThrows(SQLException.class, () -> login(other, credential));
        assertThrows(
            VaultUnavailableException.class,
            () -> vault.read("database/creds/local-" + service + "-migration"));
        assertThrows(
            VaultUnavailableException.class,
            () -> vault.read("database/creds/local-" + other + "-runtime"));
        assertThrows(
            VaultUnavailableException.class,
            () -> vault.readOptional("secret/data/local/" + other + "/otp-keys"));
      }
    }
  }

  @Test
  @Order(2)
  void issuedCredentialCanRenewAndRevokeWithoutClaimingExistingSessionsAreDisconnected()
      throws Exception {
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"))) {
      var credential = vault.read("database/creds/" + ROLE);
      String lease = credential.path("lease_id").asText();
      try (Connection existing = login("identity", credential)) {
        assertTrue(
            vault
                    .write("sys/leases/renew", Map.of("lease_id", lease))
                    .path("lease_duration")
                    .asLong()
                > 0);
        vault.write("sys/leases/revoke", Map.of("lease_id", lease));
        assertThrows(SQLException.class, () -> login("identity", credential));
        /* PostgreSQL role removal prevents new login; an established session can still complete work. */
        assertTrue(existing.isValid(1));
      }
    }
  }

  @Test
  @Order(3)
  void leasePolicyCannotRevokeAnotherServiceCredential() {
    try (VaultClient wallet = client(LOCAL.resolve("apps/wallet"));
        VaultClient identity = client(LOCAL.resolve("apps/identity"))) {
      var credential = wallet.read("database/creds/local-wallet-runtime");
      assertThrows(
          VaultUnavailableException.class,
          () ->
              identity.write(
                  "sys/leases/revoke", Map.of("lease_id", credential.path("lease_id").asText())));
    }
  }

  @Test
  @Order(4)
  void actualPoolContinuesBeyondInitialMaxTtlAndDrainsAnInFlightTransaction() throws Exception {
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"));
        LeasedDataSource pool =
            new LeasedDataSource(vault, ROLE, url("identity"), Duration.ofSeconds(10))) {
      Instant began = Instant.now();
      await()
          .atMost(Duration.ofSeconds(12))
          .until(() -> Instant.now().isAfter(began.plusSeconds(10)));
      try (Connection held = pool.getConnection();
          var statement = held.createStatement()) {
        held.setAutoCommit(false);
        statement.executeUpdate(
            "INSERT INTO " + PROBE + "(id,marker) VALUES (1,'synthetic-lease-probe')");
        await().atMost(Duration.ofSeconds(10)).until(() -> pool.replacementCount() >= 2);
        held.commit();
      }
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> {
                try (Connection connection = pool.getConnection();
                    var statement = connection.createStatement()) {
                  var rows =
                      statement.executeQuery("SELECT count(*) FROM " + PROBE + " WHERE id=1");
                  return rows.next()
                      && rows.getInt(1) == 1
                      && pool.replacementCount() >= 4
                      && Instant.now().isAfter(began.plusSeconds(45));
                }
              });
    }
  }

  @Test
  @Order(5)
  void tokenRevocationReauthenticatesAndAProcessRestartUsesItsProvisionedAppRole() {
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"))) {
      vault.read("secret/data/local/identity/otp-keys");
      vault.write("auth/token/revoke-self", Map.of());
      assertThrows(
          VaultUnavailableException.class, () -> vault.read("secret/data/local/identity/otp-keys"));
      assertTrue(vault.read("secret/data/local/identity/otp-keys").has("data"));
    }
    try (VaultClient restarted = client(LOCAL.resolve("apps/identity"))) {
      assertTrue(restarted.read("secret/data/local/identity/otp-keys").has("data"));
    }
  }

  @Test
  @Order(6)
  void managerCredentialRotationInvalidatesBootstrapPasswordAndPreservesIssuance()
      throws Exception {
    var data =
        JsonMapper.builder()
            .build()
            .readTree(Files.readString(LOCAL.resolve("identity/credentials.json")));
    assertThrows(
        SQLException.class,
        () ->
            DriverManager.getConnection(
                url("identity"), "vault_manager", data.path("manager").asText()));
    operator.write("database/rotate-root/local-identity", Map.of());
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"));
        Connection connection = login("identity", vault.read("database/creds/" + ROLE))) {
      assertTrue(connection.isValid(1));
    }
  }

  @Test
  @Order(7)
  void vaultOutageUsesOnlyValidCachedCredentialsThenFailsClosedAndRecovers() throws Exception {
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"));
        LeasedDataSource pool =
            new LeasedDataSource(vault, ROLE, url("identity"), Duration.ofSeconds(10))) {
      operator.write("sys/seal", Map.of());
      try {
        assertThrows(
            VaultUnavailableException.class,
            () ->
                vault.readOptional(
                    "secret/data/local/identity/authenticators/cus_" + "A".repeat(22)));
        try (Connection connection = pool.getConnection()) {
          assertTrue(connection.isValid(1));
        }
        await()
            .atMost(Duration.ofSeconds(35))
            .pollInterval(Duration.ofMillis(200))
            .until(
                () -> {
                  try (Connection ignored = pool.getConnection()) {
                    return false;
                  } catch (SQLException expected) {
                    return true;
                  }
                });
      } finally {
        var initialization =
            JsonMapper.builder()
                .build()
                .readTree(Files.readString(LOCAL.resolve("operator/initialization.json")));
        for (int key = 0; key < 2; key++)
          operator.write(
              "sys/unseal", Map.of("key", initialization.path("keys").get(key).asText()));
      }
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () -> {
                try (Connection connection = pool.getConnection()) {
                  return connection.isValid(1);
                } catch (SQLException expected) {
                  return false;
                }
              });
    }
  }

  @Test
  @Order(8)
  void failuresAndDiagnosticsExcludeSecretValues() {
    assertThrows(
        VaultUnavailableException.class,
        () -> operator.readOptional("missing-vault-mount/data/no-secret"));
    try (VaultClient vault = client(LOCAL.resolve("apps/wallet"))) {
      var failure =
          assertThrows(
              VaultUnavailableException.class,
              () -> vault.read("secret/data/local/identity/otp-keys"));
      assertEquals(
          "Required secret service is unavailable or access is denied", failure.getMessage());
      assertFalse(failure.getMessage().contains("hvs."));
    }
    assertThrows(
        VaultUnavailableException.class,
        () ->
            new VaultClient(
                    URI.create("https://vault:8200"),
                    LOCAL.resolve("ca.crt"),
                    LOCAL.resolve("missing-role"),
                    LOCAL.resolve("missing-secret"))
                .read("secret/data/local/identity/otp-keys"));
  }

  private static VaultClient client(Path folder) {
    return new VaultClient(
        URI.create("https://vault:8200"),
        LOCAL.resolve("ca.crt"),
        folder.resolve("role-id"),
        folder.resolve("secret-id"));
  }

  @Test
  @Order(9)
  void shortLivedAppRoleTokenRenewsWithoutReplacingItsIdentity() {
    operator.write(
        "auth/approle/role/local-identity", Map.of("token_ttl", "8s", "token_max_ttl", "30s"));
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"))) {
      String accessor = vault.read("auth/token/lookup-self").path("data").path("accessor").asText();
      Instant started = Instant.now();
      await()
          .atMost(Duration.ofSeconds(7))
          .until(() -> Instant.now().isAfter(started.plusSeconds(5)));
      var renewed = vault.read("auth/token/lookup-self").path("data");
      assertTrue(accessor.equals(renewed.path("accessor").asText()));
      assertTrue(renewed.path("ttl").asLong() >= 5);
    } finally {
      operator.write(
          "auth/approle/role/local-identity", Map.of("token_ttl", "20m", "token_max_ttl", "4h"));
    }
  }

  @Test
  @Order(10)
  void springAutoConfigurationSuppliesTheActualVaultPoolAndReadinessContributor() {
    Path scope = LOCAL.resolve("apps/identity");
    new org.springframework.boot.test.context.runner.ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                VaultDatabaseAutoConfiguration.class))
        .withPropertyValues(
            "payguard.vault.enabled=true",
            "payguard.vault.address=https://vault:8200",
            "payguard.vault.ca-file=" + LOCAL.resolve("ca.crt"),
            "payguard.vault.role-id-file=" + scope.resolve("role-id"),
            "payguard.vault.secret-id-file=" + scope.resolve("secret-id"),
            "payguard.vault.database-role=" + ROLE,
            "spring.datasource.url=" + url("identity"))
        .run(
            context -> {
              assertTrue(context.getStartupFailure() == null);
              var source = context.getBean(javax.sql.DataSource.class);
              assertTrue(source instanceof LeasedDataSource);
              try (Connection connection = source.getConnection()) {
                assertTrue(connection.isValid(1));
              }
              var health =
                  context.getBean(
                      "vaultHealthIndicator",
                      org.springframework.boot.health.contributor.HealthIndicator.class);
              assertEquals("UP", health.health().getStatus().getCode());
            });
  }

  private static String url(String service) {
    return "jdbc:postgresql://postgres-"
        + service
        + ":5432/payguard_"
        + service
        + "?sslmode=verify-full&sslrootcert=/workspace/.local/payguard/ca.crt&currentSchema=payguard";
  }

  private static Connection login(String service, JsonNode credential) throws SQLException {
    return DriverManager.getConnection(
        url(service),
        credential.path("data").path("username").asText(),
        credential.path("data").path("password").asText());
  }

  private static Connection migration(String service) throws SQLException {
    var credential = operator.read("database/creds/local-" + service + "-migration");
    Connection connection = login(service, credential);
    try (var statement = connection.createStatement()) {
      statement.execute("SET ROLE " + service + "_owner");
    }
    return connection;
  }

  @Test
  @Order(11)
  void databaseOutageDeniesConnectionsAndRecoveryDoesNotReplayATransaction() throws Exception {
    Path control = Path.of("target/vault-test-reports/control");
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"));
        LeasedDataSource pool =
            new LeasedDataSource(vault, ROLE, url("identity"), Duration.ofSeconds(10))) {
      Files.writeString(control.resolve("stop.request"), "requested");
      try {
        await()
            .atMost(Duration.ofSeconds(15))
            .until(() -> Files.exists(control.resolve("stop.ack")));
        assertThrows(SQLException.class, pool::getConnection);
      } finally {
        Files.writeString(control.resolve("start.request"), "requested");
        await()
            .atMost(Duration.ofSeconds(15))
            .until(() -> Files.exists(control.resolve("start.ack")));
      }
      await()
          .atMost(Duration.ofSeconds(20))
          .until(
              () -> {
                try (Connection connection = pool.getConnection()) {
                  return connection.isValid(1);
                } catch (SQLException expected) {
                  return false;
                }
              });
    }
  }

  @Test
  @Order(12)
  void forcedRevocationFailsBorrowingUntilAReplacementRestoresRuntimePrivileges() throws Exception {
    try (VaultClient vault = client(LOCAL.resolve("apps/identity"));
        LeasedDataSource pool =
            new LeasedDataSource(vault, ROLE, url("identity"), Duration.ofSeconds(10))) {
      try (Connection connection = pool.getConnection()) {
        assertTrue(connection.isValid(1));
      }
      operator.write("sys/leases/revoke-prefix/database/creds/" + ROLE, Map.of());
      assertThrows(SQLException.class, pool::getConnection);
      await().atMost(Duration.ofSeconds(15)).until(() -> pool.replacementCount() >= 2);
      try (Connection connection = pool.getConnection()) {
        assertTrue(connection.isValid(1));
      }
    }
  }
}
