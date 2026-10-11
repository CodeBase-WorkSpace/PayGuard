package infrastructure.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic offline import and populated PostgreSQL upgrade, without live Oracle access. */
class PostgresqlMigrationRehearsalTest {
  private static final String LOCATION =
      "filesystem:../payguard-identity-service/src/main/resources/db/migration/postgresql";

  @Test
  void v1ToV2UpgradePreservesRecordsAndRejectsTwoHistoricalActiveRows() throws Exception {
    try (var database = new PostgreSQLContainer("postgres:17.11-bookworm")) {
      database.start();
      Flyway.configure()
          .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
          .schemas("payguard")
          .defaultSchema("payguard")
          .locations(LOCATION)
          .target("1")
          .load()
          .migrate();
      try (Connection connection = connect(database);
          var statement = connection.createStatement()) {
        statement.executeUpdate(insert("A", "ACTIVE"));
        statement.executeUpdate(insert("B", "ACTIVE"));
        var next =
            Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("payguard")
                .defaultSchema("payguard")
                .locations(LOCATION)
                .load();
        assertThrows(org.flywaydb.core.api.FlywayException.class, next::migrate);
        assertEquals(2, count(connection, "identity_challenge"));
        /* This fixture explicitly resolves the synthetic duplicate; the migration never deletes it. */
        statement.executeUpdate(
            "UPDATE identity_challenge SET status='INVALIDATED' WHERE id='otp_"
                + "B".repeat(22)
                + "'");
        next.migrate();
        assertEquals(2, count(connection, "identity_challenge"));
        assertEquals(1, count(connection, "identity_challenge_scope"));
        assertEquals(2, count(connection, "flyway_schema_history"));
      }
    }
  }

  @Test
  void importerPreservesUuidReferencesNullableFieldsAndInstantsAndRefusesPopulatedDestination()
      throws Exception {
    try (var database = new PostgreSQLContainer("postgres:17.11-bookworm")) {
      database.start();
      Flyway.configure()
          .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
          .schemas("payguard")
          .defaultSchema("payguard")
          .locations(LOCATION)
          .load()
          .migrate();
      var json = JsonMapper.builder().build();
      var input =
          json.readTree(
              """
          {"challenges":[{"id":"otp_AAAAAAAAAAAAAAAAAAAAAA","user_id":"cus_aaaaaaaaaaaaaaaaaaaaaa",
          "purpose":"LOGIN","channel":"SMS","verification_mode":"RANDOM_NUMERIC","code_hash":"synthetic-hash",
          "device_binding_hash":null,"attempts":1,"max_attempts":5,"status":"USED",
          "created_at":"2026-01-01T03:30:00.123456+03:30","expires_at":"2026-01-01T00:05:00Z",
          "consumed_at":"2026-01-01T00:00:01.123456Z","retention_until":"2029-01-02T00:00:00Z"}],
          "audit":[{"id":"00112233-4455-6677-8899-aabbccddeeff","challenge_id":"otp_AAAAAAAAAAAAAAAAAAAAAA",
          "user_id":"cus_aaaaaaaaaaaaaaaaaaaaaa","action":"VERIFY","reason":"verified","source_ip":null,
          "occurred_at":"2026-01-01T00:00:01.123456Z"}],
          "manifest":{"challenge_count":1,"audit_count":1,"approval_reference":"synthetic-only",
          "mapping_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}
          """);
      try (Connection connection = connect(database)) {
        var orphan = input.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) orphan.path("audit").get(0))
            .put("challenge_id", "otp_" + "B".repeat(22));
        assertThrows(
            java.sql.SQLException.class, () -> IdentityDataImporter.importData(connection, orphan));
        assertEquals(0, count(connection, "identity_challenge"));
        assertEquals(0, count(connection, "identity_audit_log"));
        var badInteger = input.deepCopy();
        ((tools.jackson.databind.node.ObjectNode) badInteger.path("challenges").get(0))
            .put("attempts", "not-an-integer");
        assertThrows(
            java.sql.SQLException.class,
            () -> IdentityDataImporter.importData(connection, badInteger));
        assertEquals(0, count(connection, "identity_challenge"));
        IdentityDataImporter.importData(connection, input);
        assertEquals(1, count(connection, "identity_challenge"));
        assertEquals(1, count(connection, "identity_audit_log"));
        assertThrows(
            java.sql.SQLException.class, () -> IdentityDataImporter.importData(connection, input));
        try (var statement = connection.createStatement();
            var rows =
                statement.executeQuery(
                    "SELECT id,created_at,consumed_at,device_binding_hash,user_id FROM identity_challenge")) {
          assertTrue(rows.next());
          assertEquals("cus_" + "a".repeat(22), rows.getString("user_id"));
          assertEquals(
              java.time.Instant.parse("2026-01-01T00:00:00.123456Z"),
              rows.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
          assertTrue(rows.getObject("consumed_at") != null);
          assertTrue(rows.getObject("device_binding_hash") == null);
        }
      }
    }
  }

  private static Connection connect(PostgreSQLContainer database) throws java.sql.SQLException {
    return DriverManager.getConnection(
        database.getJdbcUrl() + "&currentSchema=payguard",
        database.getUsername(),
        database.getPassword());
  }

  private static int count(Connection connection, String table) throws java.sql.SQLException {
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT count(*) FROM "
                    + table
                    + " WHERE "
                    + (table.equals("flyway_schema_history")
                        ? "success AND version IS NOT NULL"
                        : "true"))) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private static String insert(String suffix, String status) {
    return "INSERT INTO identity_challenge(id,user_id,purpose,channel,verification_mode,code_hash,max_attempts,status,created_at,expires_at,retention_until) VALUES ('otp_"
        + suffix.repeat(22)
        + "','cus_"
        + "A".repeat(22)
        + "','LOGIN','SMS','RANDOM_NUMERIC','synthetic',5,'"
        + status
        + "','2026-01-01T00:00:00Z','2026-01-01T00:05:00Z','2029-01-02T00:00:00Z')";
  }
}
