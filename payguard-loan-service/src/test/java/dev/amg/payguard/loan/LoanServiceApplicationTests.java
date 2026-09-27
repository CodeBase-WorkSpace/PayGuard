package dev.amg.payguard.loan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LoanServiceApplicationTests {

  @Container
  static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
          .withUsername("PAYGUARD_TEST")
          .withPassword("payguard_test");

  @DynamicPropertySource
  static void oracleProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
    registry.add("spring.datasource.driver-class-name", ORACLE::getDriverClassName);
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.OracleDialect");
  }

  @Test
  void contextLoads() {}
}
