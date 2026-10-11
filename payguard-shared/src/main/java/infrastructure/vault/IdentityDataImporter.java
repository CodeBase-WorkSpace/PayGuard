package infrastructure.vault;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Offline parent-before-child import into an empty, separately migrated destination. */
public final class IdentityDataImporter {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(IdentityDataImporter.class);
  private static final String MANIFEST = "manifest";
  private static final String AUDIT_TABLE = "identity_audit_log";
  private static final List<String> CHALLENGE =
      List.of(
          "id",
          "user_id",
          "purpose",
          "channel",
          "verification_mode",
          "code_hash",
          "device_binding_hash",
          "attempts",
          "max_attempts",
          "status",
          "created_at",
          "expires_at",
          "consumed_at",
          "retention_until");
  private static final List<String> AUDIT =
      List.of("id", "challenge_id", "user_id", "action", "reason", "source_ip", "occurred_at");

  private IdentityDataImporter() {}

  public static void main(String[] arguments) {
    try {
      String url = System.getenv("PAYGUARD_MIGRATION_JDBC_URL");
      if (arguments.length != 1 || url == null) {
        throw new IllegalArgumentException(
            "Explicit protected input and verified PostgreSQL TLS required");
      }
      PostgreSqlTls.requireVerified(url);
      JsonNode input =
          JsonMapper.builder().build().readTree(Files.readString(Path.of(arguments[0])));
      try (Connection connection =
          DriverManager.getConnection(
              url,
              System.getenv("PAYGUARD_MIGRATION_USERNAME"),
              System.getenv("PAYGUARD_MIGRATION_PASSWORD"))) {
        try (var statement = connection.createStatement()) {
          statement.execute("SET ROLE identity_owner");
          statement.execute("SET search_path TO payguard");
        }
        importData(connection, input);
      }
      LOG.info(
          "Identity import committed: row counts, references and representative fields reconciled");
    } catch (Exception exception) {
      LOG.error("Identity import refused; protected diagnostics suppressed");
      System.exit(1);
    }
  }

  public static void importData(Connection connection, JsonNode input) throws SQLException {
    boolean originalAutoCommit = connection.getAutoCommit();
    if (!originalAutoCommit
        || input.path(MANIFEST).path("approval_reference").asText().isBlank()
        || !input.path(MANIFEST).path("mapping_sha256").asText().matches("[a-f0-9]{64}")) {
      throw new SQLException("Separate import connection and approved mapping manifest required");
    }
    connection.setAutoCommit(false);
    try {
      try (var statement = connection.createStatement()) {
        statement.execute(
            "LOCK TABLE identity_challenge, identity_audit_log IN ACCESS EXCLUSIVE MODE");
        for (String table : List.of("identity_challenge", AUDIT_TABLE)) {
          try (var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            if (!rows.next()) throw new SQLException("Reconciliation query returned no result");
            if (rows.getLong(1) != 0) throw new SQLException("Import destination is populated");
          }
        }
      }
      insert(connection, "identity_challenge", CHALLENGE, input.path("challenges"));
      insert(connection, AUDIT_TABLE, AUDIT, input.path("audit"));
      try (var statement = connection.createStatement()) {
        statement.executeUpdate(
            "INSERT INTO identity_challenge_scope(user_id,purpose) SELECT DISTINCT user_id,purpose FROM identity_challenge ON CONFLICT DO NOTHING");
        try (var rows =
            statement.executeQuery(
                "SELECT count(*) FROM identity_audit_log a JOIN identity_challenge c ON c.id=a.challenge_id WHERE c.user_id<>a.user_id")) {
          if (!rows.next()) throw new SQLException("Reconciliation query returned no result");
          if (rows.getLong(1) != 0) throw new SQLException("Audit subject mismatch");
        }
      }
      reconcile(
          connection,
          "identity_challenge",
          CHALLENGE,
          input.path("challenges"),
          input.path(MANIFEST).path("challenge_count").asInt(-1));
      reconcile(
          connection,
          AUDIT_TABLE,
          AUDIT,
          input.path("audit"),
          input.path(MANIFEST).path("audit_count").asInt(-1));
      connection.commit();
    } catch (SQLException | RuntimeException exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(originalAutoCommit);
    }
  }

  private static void insert(
      Connection connection, String table, List<String> columns, JsonNode data)
      throws SQLException {
    if (!data.isArray()) throw new SQLException("Expected export rows");
    String placeholders = String.join(",", java.util.Collections.nCopies(columns.size(), "?"));
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + "("
                + String.join(",", columns)
                + ") VALUES ("
                + placeholders
                + ")")) {
      for (JsonNode row : data) {
        for (int i = 0; i < columns.size(); i++)
          statement.setObject(i + 1, value(table, columns.get(i), row));
        statement.executeUpdate();
      }
    }
  }

  private static Object value(String table, String column, JsonNode row) throws SQLException {
    JsonNode node = row.get(column);
    if (node == null) throw new SQLException("Export column missing");
    if (node.isNull()) return null;
    if (column.endsWith("_at") || "retention_until".equals(column))
      return OffsetDateTime.parse(node.asText());
    if ("attempts".equals(column) || "max_attempts".equals(column)) {
      if (!node.isIntegralNumber() || !node.canConvertToInt())
        throw new SQLException("Invalid exported integer");
      return node.asInt();
    }
    if (!node.isTextual()) throw new SQLException("Invalid exported text");
    if (AUDIT_TABLE.equals(table) && "id".equals(column)) return UUID.fromString(node.asText());
    return node.asText();
  }

  private static void reconcile(
      Connection connection, String table, List<String> columns, JsonNode data, int expected)
      throws SQLException {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
      if (!rows.next()) throw new SQLException("Reconciliation query returned no result");
      if (rows.getInt(1) != expected || expected != data.size())
        throw new SQLException("Export count mismatch");
    }
    try (var statement =
        connection.prepareStatement(
            "SELECT " + String.join(",", columns) + " FROM " + table + " WHERE id=?")) {
      for (JsonNode row : data) {
        statement.setObject(1, value(table, "id", row));
        try (var rows = statement.executeQuery()) {
          if (!rows.next()) throw new SQLException("Export identity missing");
          for (String column : columns) {
            Object expectedValue = value(table, column, row);
            Object stored =
                expectedValue instanceof OffsetDateTime
                    ? rows.getObject(column, OffsetDateTime.class)
                    : rows.getObject(column);
            if (stored instanceof OffsetDateTime timestamp
                && expectedValue instanceof OffsetDateTime source) {
              if (!timestamp.toInstant().equals(source.toInstant()))
                throw new SQLException("Export instant mismatch");
            } else if (!java.util.Objects.equals(expectedValue, stored)) {
              throw new SQLException("Export field mismatch");
            }
          }
        }
      }
    }
  }
}
