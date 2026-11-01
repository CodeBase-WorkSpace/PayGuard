package dev.amg.payguard.loan;

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
class LoanServiceApplicationTests {
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
  void uuidExactAmountsLocalDateAndNullableRetentionRoundTrip() {
    var id = java.util.UUID.randomUUID();
    var collateral = java.util.UUID.randomUUID();
    var principal = new java.math.BigDecimal("12345678901234567890.12345678");
    var rate = new java.math.BigDecimal("0.123456789012");
    var date = java.time.LocalDate.of(2026, 1, 1);
    jdbc.update(
        "INSERT INTO loan_account(id,borrower_token,collateral_id,principal,annual_rate,term_months,status,outstanding_principal,accrued_interest,last_accrual_date) VALUES (?,?,?,?,?,12,'ACTIVE',?,0,?)",
        id,
        "synthetic-subject",
        collateral,
        principal,
        rate,
        principal,
        date);
    jdbc.query(
        "SELECT id,collateral_id,principal,annual_rate,last_accrual_date,retention_until FROM loan_account WHERE id=?",
        rows -> {
          org.junit.jupiter.api.Assertions.assertEquals(
              id, rows.getObject("id", java.util.UUID.class));
          org.junit.jupiter.api.Assertions.assertEquals(
              collateral, rows.getObject("collateral_id", java.util.UUID.class));
          org.junit.jupiter.api.Assertions.assertEquals(principal, rows.getBigDecimal("principal"));
          org.junit.jupiter.api.Assertions.assertEquals(rate, rows.getBigDecimal("annual_rate"));
          org.junit.jupiter.api.Assertions.assertEquals(
              date, rows.getObject("last_accrual_date", java.time.LocalDate.class));
          org.junit.jupiter.api.Assertions.assertNull(rows.getObject("retention_until"));
        },
        id);
  }

  @Test
  void duplicateProcessedEventAndOutboxIdentifiersAreRejected() {
    var event = java.util.UUID.randomUUID();
    jdbc.update(
        "INSERT INTO loan_processed_event(event_id,consumer_name) VALUES (?,'synthetic-consumer')",
        event);
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO loan_processed_event(event_id,consumer_name) VALUES (?,'synthetic-consumer')",
                event));
    jdbc.update(
        "INSERT INTO loan_outbox(event_id,event_type,aggregate_id,payload,occurred_at) VALUES (?,'synthetic-event','synthetic-id','{}',CURRENT_TIMESTAMP)",
        event);
    org.junit.jupiter.api.Assertions.assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO loan_outbox(event_id,event_type,aggregate_id,payload,occurred_at) VALUES (?,'synthetic-event','synthetic-id','{}',CURRENT_TIMESTAMP)",
                event));
  }
}
