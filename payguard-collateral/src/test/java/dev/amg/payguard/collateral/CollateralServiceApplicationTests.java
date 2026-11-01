package dev.amg.payguard.collateral;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CollateralServiceApplicationTests {
  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.11-bookworm")
          .withInitScript("postgres-test-init.sql")
          .withUsername("payguard_test")
          .withPassword("payguard_test");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=payguard");
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @Test
  void contextLoads() {
    org.junit.jupiter.api.Assertions.assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
  }

  @Test
  void valuationPreservesUuidMoneyCurrencyAndTimestampPrecision() {
    var id = java.util.UUID.randomUUID();
    var loan = java.util.UUID.randomUUID();
    var amount = new java.math.BigDecimal("99999999999999999999.12345678");
    var fx = new java.math.BigDecimal("1.123456789012");
    var observed = java.time.Instant.parse("2026-01-01T12:00:00.123456Z");
    jdbc.update(
        "INSERT INTO collateral(id,owner_token,loan_id,amount,asset_currency,warning_ltv,margin_ltv,liquidation_ltv,status) VALUES (?,'synthetic-owner',?,?,'EUR',0.5,0.6,0.8,'LOCKED')",
        id,
        loan,
        amount);
    jdbc.update(
        "INSERT INTO collateral_valuation(collateral_id,asset_value,fx_rate,observed_at) VALUES (?,?,?,?)",
        id,
        amount,
        fx,
        java.time.OffsetDateTime.ofInstant(observed, java.time.ZoneOffset.UTC));
    jdbc.query(
        "SELECT c.id,c.loan_id,c.amount,c.asset_currency,c.retention_until,v.fx_rate,v.observed_at FROM collateral c JOIN collateral_valuation v ON v.collateral_id=c.id WHERE c.id=?",
        rows -> {
          org.junit.jupiter.api.Assertions.assertEquals(
              id, rows.getObject("id", java.util.UUID.class));
          org.junit.jupiter.api.Assertions.assertEquals(
              loan, rows.getObject("loan_id", java.util.UUID.class));
          org.junit.jupiter.api.Assertions.assertEquals(amount, rows.getBigDecimal("amount"));
          org.junit.jupiter.api.Assertions.assertEquals("EUR", rows.getString("asset_currency"));
          org.junit.jupiter.api.Assertions.assertEquals(fx, rows.getBigDecimal("fx_rate"));
          org.junit.jupiter.api.Assertions.assertEquals(
              observed, rows.getObject("observed_at", java.time.OffsetDateTime.class).toInstant());
          org.junit.jupiter.api.Assertions.assertNull(rows.getObject("retention_until"));
        },
        id);
  }

  @Test
  void valuationCannotReferenceMissingOwnedCollateral() {
    var missing = java.util.UUID.randomUUID();
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO collateral_valuation(collateral_id,asset_value,fx_rate,observed_at) VALUES (?,1,1,CURRENT_TIMESTAMP)",
                missing));
  }
}
