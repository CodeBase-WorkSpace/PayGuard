package infrastructure.vault;

import java.sql.DriverManager;
import java.util.Set;
import org.flywaydb.core.Flyway;

/** Separate operator entry point. Migration logins SET ROLE to the no-login owner before DDL. */
public final class SchemaMigrator {
  private static final String IDENTITY = "identity";
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(SchemaMigrator.class);

  private SchemaMigrator() {}

  public static void main(String[] arguments) {
    try {
      String service = required("PAYGUARD_MIGRATION_SERVICE");
      if (!Set.of("identity", "loan", "collateral").contains(service)) {
        throw new IllegalArgumentException("No supplied schema for this service");
      }
      String url = required("PAYGUARD_MIGRATION_JDBC_URL");
      PostgreSqlTls.requireVerified(url);
      String user = required("PAYGUARD_MIGRATION_USERNAME");
      String password = required("PAYGUARD_MIGRATION_PASSWORD");
      Flyway.configure()
          .dataSource(url, user, password)
          .initSql("SET ROLE " + service + "_owner")
          .schemas("payguard")
          .defaultSchema("payguard")
          .createSchemas(false)
          .cleanDisabled(true)
          .locations(
              "filesystem:/workspace/payguard-"
                  + service
                  + "-service/src/main/resources/db/migration/postgresql")
          .load()
          .migrate();
      try (var connection = DriverManager.getConnection(url, user, password);
          var statement = connection.createStatement()) {
        statement.execute("SET ROLE " + service + "_owner");
        statement.execute("ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC");
        statement.execute(
            "REVOKE ALL ON payguard.flyway_schema_history FROM " + service + "_runtime");
        if (IDENTITY.equals(service)) {
          statement.execute(
              "REVOKE UPDATE, DELETE ON payguard.identity_audit_log FROM identity_runtime");
          statement.execute(
              "REVOKE DELETE ON payguard.identity_challenge_scope FROM identity_runtime");
        } else {
          statement.execute(
              "REVOKE UPDATE, DELETE ON payguard."
                  + service
                  + "_audit_log FROM "
                  + service
                  + "_runtime");
        }
      }
    } catch (RuntimeException | java.sql.SQLException exception) {
      LOG.error("Migration failed; secret-bearing connection diagnostics suppressed");
      System.exit(1);
    }
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing protected migration configuration");
    }
    return value;
  }
}
