package dev.amg.payguard.identity.verification.application;

import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.Tables.IDENTITY_OTP;
import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.Tables.IDENTITY_OUTBOX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq.JooqOTPRepositoryAdapter;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq.JsonOtpOutboxEncoder;
import dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq.OtpOutboxEncoder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
class JooqOtpRepositoryIntegrationTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final CustomerId CUSTOMER = CustomerId.parse("cus_ABCDEFGHIJKLMNOPQRSTUV");
  private static final OtpId FIRST = OtpId.parse("otp_ABCDEFGHIJKLMNOPQRSTUV");
  private static final OtpId SECOND = OtpId.parse("otp_ZYXWVUTSRQPONMLKJIHGFE");

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-bookworm"));

  private static DSLContext dsl;

  @BeforeAll
  static void database() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setURL(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("payguard")
        .defaultSchema("payguard")
        .locations("classpath:db/migration/postgresql")
        .load()
        .migrate();
    dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
  }

  @Test
  void replacesActiveChallengeAndPersistsLockedMutation() {
    Clock clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
    OtpOutboxEncoder encoder = new JsonOtpOutboxEncoder(JsonMapper.builder().build());
    dsl.transaction(
        configuration -> {
          DSLContext tx = DSL.using(configuration);
          JooqOTPRepositoryAdapter repository = new JooqOTPRepositoryAdapter(tx, clock, encoder);
          repository.replaceActive(issue(FIRST));
          repository.replaceActive(issue(SECOND));

          assertEquals(
              OtpStatus.INVALIDATED,
              repository.findById(FIRST).orElseThrow().getSnapshot().status());
          assertEquals(
              OtpStatus.ACTIVE, repository.findById(SECOND).orElseThrow().getSnapshot().status());
          assertFalse(
              repository
                  .withLockedChallenge(
                      OtpId.parse("otp_0000000000000000000000"), otp -> "unexpected")
                  .isPresent());
          assertEquals(
              "invalidated",
              repository
                  .withLockedChallenge(
                      SECOND,
                      otp -> {
                        otp.invalidate(NOW.plusSeconds(20));
                        return "invalidated";
                      })
                  .orElseThrow());
          assertEquals(
              OtpStatus.INVALIDATED,
              repository.findById(SECOND).orElseThrow().getSnapshot().status());
          assertEquals(4, tx.fetchCount(IDENTITY_OUTBOX));
          assertEquals(2, tx.fetchCount(IDENTITY_OTP));
          for (var payload :
              tx.select(IDENTITY_OUTBOX.PAYLOAD)
                  .from(IDENTITY_OUTBOX)
                  .fetch(IDENTITY_OUTBOX.PAYLOAD)) {
            assertFalse(payload.data().contains("hash:123456"));
          }
        });
  }

  private static OTP issue(OtpId id) {
    Instant expires = NOW.plusSeconds(300);
    return OTP.issue(
        id,
        CUSTOMER,
        new OtpSnapshot(
            OtpPurpose.LOGIN,
            OtpChannel.SMS,
            OtpVerificationMode.RANDOM_NUMERIC,
            "hash:123456",
            null,
            3,
            NOW,
            expires,
            expires.atZone(ZoneOffset.UTC).plusYears(3).toInstant(),
            0,
            OtpStatus.ACTIVE,
            null));
  }
}
