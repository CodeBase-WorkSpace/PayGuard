package infrastructure.vault;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.AbstractDataSource;

/**
 * Replaces validated pool generations before lease expiry. Borrowers already in a transaction keep
 * their generation during a bounded drain. This class never retries application transactions.
 */
public final class LeasedDataSource extends AbstractDataSource implements AutoCloseable {
  private static final int MAX_RETIRED_GENERATIONS = 8;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(1);
  private final VaultClient vault;
  private final String role;
  private final String jdbcUrl;
  private final Duration drain;
  private final List<Generation> retired = new ArrayList<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("database-lease-maintenance").factory());
  private @Nullable Generation current;
  private boolean closed;
  private long replacements;
  private Instant nextAttempt = Instant.EPOCH;
  private boolean rotationRequested;

  public LeasedDataSource(VaultClient vault, String role, String jdbcUrl, Duration drain) {
    PostgreSqlTls.requireVerified(jdbcUrl);
    if (!role.matches("[a-z][a-z0-9-]+-runtime")) {
      throw new IllegalArgumentException(
          "Runtime database requires a scoped role and verified PostgreSQL TLS");
    }
    this.vault = vault;
    this.role = role;
    this.jdbcUrl = jdbcUrl;
    this.drain = drain;
    replace();
    scheduler.scheduleWithFixedDelay(this::maintain, 1, 1, TimeUnit.SECONDS);
  }

  private synchronized void replace() {
    if (retired.size() >= MAX_RETIRED_GENERATIONS) {
      throw new VaultUnavailableException();
    }
    Instant issued = Instant.now();
    var secret = vault.read("database/creds/" + role);
    String lease = secret.path("lease_id").asText();
    long ttl = secret.path("lease_duration").asLong();
    if (ttl < 20 || !lease.startsWith("database/creds/" + role + "/")) {
      throw new VaultUnavailableException();
    }
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(secret.path("data").path("username").asText());
    config.setPassword(secret.path("data").path("password").asText());
    config.setPoolName("payguard-runtime-" + replacements);
    config.setMaximumPoolSize(4);
    config.setMinimumIdle(0);
    config.setConnectionTimeout(1000);
    config.setValidationTimeout(1000);
    config.setInitializationFailTimeout(1000);
    config.setConnectionInitSql("SET search_path TO payguard");
    HikariDataSource candidate;
    try {
      candidate = new HikariDataSource(config);
    } catch (RuntimeException exception) {
      revoke(lease);
      throw new VaultUnavailableException(exception);
    }
    try (var connection = candidate.getConnection()) {
      if (!connection.isValid(1) || !Instant.now().isBefore(issued.plusSeconds(ttl - 2))) {
        throw new SQLException("Database validation failed");
      }
      validateRuntime(connection);
    } catch (SQLException exception) {
      candidate.close();
      revoke(lease);
      throw new VaultUnavailableException(exception);
    }
    Generation previous = current;
    current =
        new Generation(candidate, lease, issued.plusSeconds(ttl / 2), issued.plusSeconds(ttl - 2));
    replacements++;
    nextAttempt = Instant.EPOCH;
    rotationRequested = false;
    if (previous != null) {
      previous.retireAt = Instant.now().plus(drain);
      previous.pool.getHikariPoolMXBean().softEvictConnections();
      retired.add(previous);
    }
  }

  private synchronized void maintain() {
    if (closed) {
      return;
    }
    Instant now = Instant.now();
    Generation generation = current;
    if (generation != null
        && (rotationRequested || !now.isBefore(generation.replaceAt))
        && !now.isBefore(nextAttempt)) {
      try {
        replace();
      } catch (VaultUnavailableException exception) {
        /* Retry once per scheduled interval while the prior lease remains valid. */
        nextAttempt = Instant.now().plus(RETRY_DELAY);
      }
    }
    retired.removeIf(
        old -> {
          if (old.pool.isClosed()
              || old.pool.getHikariPoolMXBean().getActiveConnections() == 0
              || !now.isBefore(old.retireAt)
              || !now.isBefore(old.deadline)) {
            old.pool.close();
            return revoke(old.lease);
          }
          return false;
        });
    if (generation != null && !now.isBefore(generation.deadline)) {
      generation.pool.close();
    }
  }

  private boolean revoke(String lease) {
    try {
      vault.write("sys/leases/revoke", Map.of("lease_id", lease));
      return true;
    } catch (VaultUnavailableException exception) {
      /* The engine also revokes expired leases. Retry graceful revocation during maintenance. */
      return false;
    }
  }

  @Override
  public synchronized Connection getConnection() throws SQLException {
    Generation generation = current;
    if (closed || generation == null || !Instant.now().isBefore(generation.deadline)) {
      throw new SQLException("Runtime database credential is unavailable");
    }
    try {
      Connection connection = generation.pool.getConnection();
      try {
        validateRuntime(connection);
        return connection;
      } catch (SQLException exception) {
        connection.close();
        throw exception;
      }
    } catch (SQLException exception) {
      rotationRequested = true;
      throw new SQLException(
          "Runtime database connection is unavailable",
          VaultUnavailableException.sanitizedCause(exception));
    }
  }

  private static void validateRuntime(Connection connection) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.setQueryTimeout(1);
      try (var rows =
          statement.executeQuery(
              "SELECT has_schema_privilege(current_user,'payguard','USAGE') AND has_database_privilege(current_user,current_database(),'CONNECT')")) {
        if (!rows.next() || !rows.getBoolean(1))
          throw new SQLException("Runtime database privileges unavailable");
      }
    }
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    throw new SQLException("Caller-supplied database credentials are not supported");
  }

  /** Non-sensitive count used to verify continuous replacement without exposing credentials. */
  public synchronized long replacementCount() {
    return replacements;
  }

  @Override
  public synchronized void close() {
    closed = true;
    scheduler.shutdownNow();
    if (current != null) {
      current.pool.close();
      revoke(current.lease);
    }
    for (Generation old : retired) {
      old.pool.close();
      revoke(old.lease);
    }
    retired.clear();
  }

  private static final class Generation {
    private final HikariDataSource pool;
    private final String lease;
    private final Instant replaceAt;
    private final Instant deadline;
    private Instant retireAt = Instant.MAX;

    private Generation(HikariDataSource pool, String lease, Instant replaceAt, Instant deadline) {
      this.pool = pool;
      this.lease = lease;
      this.replaceAt = replaceAt;
      this.deadline = deadline;
    }
  }
}
