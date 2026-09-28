package dev.amg.payguard.otp.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_OTP_INTEGRATION_TESTS", matches = "true")
class OtpInfrastructureIntegrationTest {

  @Container
  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

  @Test
  void oracleAndRedisAreReachable() {
    assertTrue(ORACLE.isRunning());
    assertTrue(REDIS.isRunning());
    assertTrue(ORACLE.getJdbcUrl().startsWith("jdbc:oracle:"));
  }
}
