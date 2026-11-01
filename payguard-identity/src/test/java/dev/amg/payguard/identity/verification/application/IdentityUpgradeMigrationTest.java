package dev.amg.payguard.identity.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Replays the committed history before upgrading populated Identity tables. */
@Testcontainers
class IdentityUpgradeMigrationTest {
  @Container
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

  @Test
  void preservesExistingRowsReferencesAndMigrationChecksums() throws SQLException {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas("payguard")
        .defaultSchema("payguard")
        .target("2")
        .load()
        .migrate();
    try (Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setSchema("payguard");
      List<Integer> originalChecksums = checksums(connection);
      assertEquals(2, originalChecksums.size());
      try (var sql = connection.createStatement()) {
        sql.executeUpdate(
            "INSERT INTO identity_challenge"
                + " (id,user_id,purpose,channel,verification_mode,code_hash,attempts,max_attempts,status,created_at,expires_at,retention_until)"
                + " VALUES"
                + " ('otp_ABCDEFGHIJKLMNOPQRSTUV','cus_ABCDEFGHIJKLMNOPQRSTUV','LOGIN','SMS','RANDOM_NUMERIC','synthetic-hash',0,3,'ACTIVE','2026-01-01Z','2026-01-01"
                + " 00:05Z','2029-01-01 00:05Z')");
        sql.executeUpdate(
            "INSERT INTO identity_challenge_scope VALUES ('cus_ABCDEFGHIJKLMNOPQRSTUV','LOGIN')");
        sql.executeUpdate(
            "INSERT INTO identity_audit_log (id,challenge_id,user_id,action,reason,occurred_at)"
                + " VALUES"
                + " ('00000000-0000-0000-0000-000000000001','otp_ABCDEFGHIJKLMNOPQRSTUV','cus_ABCDEFGHIJKLMNOPQRSTUV','ISSUED','synthetic','2026-01-01Z')");
      }
      Flyway.configure()
          .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
          .schemas("payguard")
          .defaultSchema("payguard")
          .load()
          .migrate();
      assertEquals(originalChecksums, checksums(connection));
      try (var sql = connection.createStatement();
          var rows =
              sql.executeQuery(
                  "SELECT c.id,c.user_id,c.status::text,a.otp_id,c.retention_until FROM"
                      + " identity_otp c JOIN identity_audit_log a ON c.id=a.otp_id")) {
        rows.next();
        assertEquals("otp_ABCDEFGHIJKLMNOPQRSTUV", rows.getString(1));
        assertEquals("cus_ABCDEFGHIJKLMNOPQRSTUV", rows.getString(2));
        assertEquals("ACTIVE", rows.getString(3));
        assertEquals(rows.getString(1), rows.getString(4));
        assertEquals(
            java.time.Instant.parse("2029-01-01T00:05:00Z"),
            rows.getObject(5, java.time.OffsetDateTime.class).toInstant());
      }
      try (var sql = connection.createStatement()) {
        var failure =
            assertThrows(
                SQLException.class,
                () ->
                    sql.executeUpdate(
                        "DELETE FROM identity_otp WHERE id='otp_ABCDEFGHIJKLMNOPQRSTUV'"));
        assertEquals("23503", failure.getSQLState());
        assertEquals(
            1,
            sql.executeUpdate(
                "UPDATE identity_otp SET status='INVALIDATED' WHERE"
                    + " id='otp_ABCDEFGHIJKLMNOPQRSTUV'"));
      }
      try (var sql = connection.createStatement();
          var rows =
              sql.executeQuery(
                  "SELECT to_regclass('payguard.identity_challenge'),(SELECT count(*) FROM"
                      + " identity_otp_scope),(SELECT count(*) FROM identity_outbox),(SELECT"
                      + " count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL)")) {
        rows.next();
        assertNull(rows.getString(1));
        assertEquals(1, rows.getInt(2));
        assertEquals(0, rows.getInt(3));
        assertEquals(4, rows.getInt(4));
      }
    }
  }

  private static List<Integer> checksums(Connection connection) throws SQLException {
    List<Integer> values = new ArrayList<>();
    try (var sql = connection.createStatement();
        var rows =
            sql.executeQuery(
                "SELECT checksum FROM flyway_schema_history WHERE version IN ('1','2') ORDER BY"
                    + " installed_rank")) {
      while (rows.next()) values.add(rows.getInt(1));
    }
    return values;
  }
}
