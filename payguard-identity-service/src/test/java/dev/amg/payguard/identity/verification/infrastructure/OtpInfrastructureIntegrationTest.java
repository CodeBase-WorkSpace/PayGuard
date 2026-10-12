package dev.amg.payguard.identity.verification.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import dev.amg.payguard.identity.IdentityApp;
import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.application.IssueOtpCommand;
import dev.amg.payguard.identity.verification.application.OtpApplicationService;
import dev.amg.payguard.identity.verification.application.OtpVerificationCommand;
import dev.amg.payguard.identity.verification.application.OtpVerificationException;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeGenerator;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(
    classes = IdentityApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class OtpInfrastructureIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.11-bookworm").withInitScript("postgres-test-init.sql");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:8.0.3-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "&currentSchema=payguard");
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired OtpRepository repository;
  @Autowired OtpApplicationService service;
  @Autowired HmacOtpCodeHasher hasher;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired dev.amg.payguard.identity.verification.application.port.VerificationQuota quota;
  @Autowired dev.amg.payguard.identity.verification.application.port.VerificationAudit audit;
  @MockitoBean OtpEventPublisher events;
  @MockitoBean OtpCodeGenerator codes;

  @BeforeEach
  void reset() {
    jdbc.update("DELETE FROM identity_audit_log");
    jdbc.update("DELETE FROM identity_challenge");
    try (var connection = redis.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    when(codes.generate()).thenReturn("123456");
  }

  @Test
  void postgresAndRedisAreReachable() {
    assertTrue(POSTGRES.isRunning());
    assertTrue(REDIS.isRunning());
    assertTrue(POSTGRES.getJdbcUrl().startsWith("jdbc:postgresql:"));
  }

  @Test
  void actualApplicationWiringIssuesVerifiesPersistsAndRejectsReplay() {
    var customer = customer("A");
    var issued =
        service.issue(
            new IssueOtpCommand(
                customer, OtpPurpose.LOGIN, OtpChannel.SMS, "phone-ref", null, "127.0.0.1"));
    var verified =
        service.verify(
            new OtpVerificationCommand(
                issued.challengeId(), customer, OtpPurpose.LOGIN, "123456", null, "127.0.0.1"));
    assertEquals(issued.challengeId(), verified.challengeId());
    OTP stored = repository.findById(issued.challengeId()).orElseThrow();
    assertEquals(customer, stored.getCustomerId());
    assertEquals(OtpStatus.USED, stored.getSnapshot().status());
    assertEquals(1, stored.getSnapshot().attempts());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM identity_audit_log", Integer.class));
    assertEquals(
        OtpVerificationResult.ALREADY_USED,
        assertThrows(
                OtpVerificationException.class,
                () ->
                    service.verify(
                        new OtpVerificationCommand(
                            issued.challengeId(),
                            customer,
                            OtpPurpose.LOGIN,
                            "123456",
                            null,
                            "127.0.0.1")))
            .result());
  }

  @Test
  void replacementInvalidatesOnlySameCustomerAndPurposeAndPreservesCase() {
    OTP first = challenge("A", customer("A"), OtpPurpose.LOGIN, "hash");
    OTP otherPurpose = challenge("B", customer("A"), OtpPurpose.PROFILE_CHANGE, "hash");
    OTP otherCustomer = challenge("C", customer("B"), OtpPurpose.LOGIN, "hash");
    repository.save(first);
    repository.save(otherPurpose);
    repository.save(otherCustomer);
    OTP replacement = challenge("a", customer("A"), OtpPurpose.LOGIN, "hash");
    repository.replaceActive(replacement);
    assertEquals(
        OtpStatus.INVALIDATED,
        repository.findById(first.getId()).orElseThrow().getSnapshot().status());
    for (OTP active : new OTP[] {otherPurpose, otherCustomer, replacement}) {
      assertEquals(
          OtpStatus.ACTIVE,
          repository.findById(active.getId()).orElseThrow().getSnapshot().status());
    }
    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM identity_challenge", Integer.class));
  }

  @Test
  void replacementFailureRollsBackEarlierInvalidation() {
    OTP original = challenge("A", customer("A"), OtpPurpose.LOGIN, "hash");
    repository.save(original);
    OTP oversized = challenge("B", customer("A"), OtpPurpose.LOGIN, "x".repeat(513));
    assertThrows(RuntimeException.class, () -> repository.replaceActive(oversized));
    assertEquals(
        OtpStatus.ACTIVE,
        repository.findById(original.getId()).orElseThrow().getSnapshot().status());
    assertTrue(repository.findById(oversized.getId()).isEmpty());
  }

  @Test
  void finalSuccessAndFinalFailureRoundTripWithoutExhaustedActiveState() {
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    for (boolean valid : new boolean[] {false, true}) {
      OtpId id = OtpId.parse("otp_" + (valid ? "A" : "B").repeat(22));
      OTP otp =
          OTP.issue(
              id,
              customer("A"),
              new OtpSnapshot(
                  OtpPurpose.LOGIN,
                  OtpChannel.SMS,
                  OtpVerificationMode.RANDOM_NUMERIC,
                  hasher.hash("123456"),
                  null,
                  1,
                  now,
                  now.plusSeconds(300),
                  now.plusSeconds(1097L * 86_400),
                  0,
                  OtpStatus.ACTIVE,
                  null));
      repository.save(otp);
      repository.withLockedChallenge(
          id,
          current ->
              current.verify(
                  new VerifyOtpCommand(
                      OtpPurpose.LOGIN, valid ? "123456" : "000000", null, now, false),
                  hasher));
      OtpSnapshot stored = repository.findById(id).orElseThrow().getSnapshot();
      assertEquals(valid ? OtpStatus.USED : OtpStatus.LOCKED, stored.status());
      assertEquals(1, stored.attempts());
    }
  }

  @Test
  void redisActuallyEnforcesIssuanceQuotaAcrossRequests() {
    var command =
        new IssueOtpCommand(
            customer("A"), OtpPurpose.LOGIN, OtpChannel.SMS, "phone-ref", null, "127.0.0.1");
    for (int index = 0; index < 3; index++) service.issue(command);
    assertThrows(
        dev.amg.payguard.identity.verification.application.OtpRateLimitExceededException.class,
        () -> service.issue(command));
    assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM identity_challenge", Integer.class));
    assertEquals(
        "4",
        redis
            .opsForValue()
            .get("identity:rate:issue:" + customer("A").getValue() + ":LOGIN:127.0.0.1"));
  }

  @Test
  void archivedOracleMigrationRetainsPopulatedDataGuard() throws Exception {
    repository.save(challenge("A", customer("A"), OtpPurpose.LOGIN, "hash"));
    String migration;
    try (var stream =
        getClass().getResourceAsStream("/db/legacy/oracle/V2__prefixed_challenge_ids.sql")) {
      migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    String guard = migration.substring(migration.indexOf("DECLARE"), migration.indexOf("\n/"));
    assertTrue(guard.contains("RAISE_APPLICATION_ERROR(-20001"));
    assertTrue(guard.contains("approved mapping"));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM identity_challenge", Integer.class));
  }

  @Test
  void concurrentRedisQuotaAcquisitionAllowsOnlyConfiguredNumber() throws Exception {
    var request =
        new dev.amg.payguard.identity.verification.application.port.VerificationQuota.Request(
            customer("A"),
            OtpPurpose.LOGIN,
            "127.0.0.1",
            dev.amg.payguard.identity.verification.application.port.VerificationQuota.Operation
                .ISSUE);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var tasks =
          java.util.stream.IntStream.range(0, 32)
              .mapToObj(
                  index -> (java.util.concurrent.Callable<Boolean>) () -> quota.tryAcquire(request))
              .toList();
      int accepted = 0;
      for (var result : executor.invokeAll(tasks)) if (result.get()) accepted++;
      assertEquals(3, accepted);
    }
    String key = "identity:rate:issue:" + customer("A").getValue() + ":LOGIN:127.0.0.1";
    assertEquals("32", redis.opsForValue().get(key));
    assertTrue(redis.getExpire(key) > 0);
  }

  @Test
  void competingSuccessfulVerificationsCommitOnlyOneFreshSuccess() throws Exception {
    var issued =
        service.issue(
            new IssueOtpCommand(
                customer("A"), OtpPurpose.LOGIN, OtpChannel.SMS, "phone-ref", null, "127.0.0.1"));
    var outcomes = verifyTogether(issued.challengeId(), "123456", 2);
    assertEquals(
        1, outcomes.stream().filter(result -> result == OtpVerificationResult.VERIFIED).count());
    assertEquals(
        1,
        outcomes.stream().filter(result -> result == OtpVerificationResult.ALREADY_USED).count());
    assertEquals(
        1, repository.findById(issued.challengeId()).orElseThrow().getSnapshot().attempts());
  }

  @Test
  void competingFailedAttemptsNeverLoseIncrementsOrExceedLimit() throws Exception {
    var issued =
        service.issue(
            new IssueOtpCommand(
                customer("A"), OtpPurpose.LOGIN, OtpChannel.SMS, "phone-ref", null, "127.0.0.1"));
    var outcomes = verifyTogether(issued.challengeId(), "000000", 8);
    assertEquals(
        4,
        outcomes.stream().filter(result -> result == OtpVerificationResult.INVALID_CODE).count());
    assertEquals(
        4, outcomes.stream().filter(result -> result == OtpVerificationResult.LOCKED).count());
    OtpSnapshot stored = repository.findById(issued.challengeId()).orElseThrow().getSnapshot();
    assertEquals(5, stored.attempts());
    assertEquals(OtpStatus.LOCKED, stored.status());
  }

  @Test
  void firstIssuanceAndReplacementAreSerializedWithoutAnExistingActiveRow() throws Exception {
    for (boolean existing : new boolean[] {false, true}) {
      var subject = customer(existing ? "B" : "A");
      if (existing) repository.save(challenge("C", subject, OtpPurpose.LOGIN, "hash"));
      OTP first = challenge(existing ? "D" : "A", subject, OtpPurpose.LOGIN, "hash");
      OTP second = challenge(existing ? "E" : "B", subject, OtpPurpose.LOGIN, "hash");
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        var one =
            executor.submit(
                () -> {
                  await(barrier);
                  repository.replaceActive(first);
                });
        var two =
            executor.submit(
                () -> {
                  await(barrier);
                  repository.replaceActive(second);
                });
        one.get(15, java.util.concurrent.TimeUnit.SECONDS);
        two.get(15, java.util.concurrent.TimeUnit.SECONDS);
      }
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT count(*) FROM identity_challenge WHERE user_id=? AND purpose='LOGIN' AND status='ACTIVE'",
              Integer.class,
              subject.getValue()));
      assertEquals(
          existing ? 3 : 2,
          jdbc.queryForObject(
              "SELECT count(*) FROM identity_challenge WHERE user_id=?",
              Integer.class,
              subject.getValue()));
    }
  }

  @Test
  void expiredAndWrongOwnerRejectionsPreserveTheirPersistencePolicy() {
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    OtpId id = OtpId.parse("otp_" + "A".repeat(22));
    repository.save(
        OTP.issue(
            id,
            customer("A"),
            new OtpSnapshot(
                OtpPurpose.LOGIN,
                OtpChannel.SMS,
                OtpVerificationMode.RANDOM_NUMERIC,
                hasher.hash("123456"),
                null,
                5,
                now.minusSeconds(600),
                now.minusSeconds(300),
                now.plusSeconds(1097L * 86_400),
                0,
                OtpStatus.ACTIVE,
                null)));
    var wrongOwner =
        new OtpVerificationCommand(
            id, customer("B"), OtpPurpose.LOGIN, "123456", null, "127.0.0.1");
    assertThrows(
        dev.amg.payguard.identity.verification.application.OtpChallengeNotFoundException.class,
        () -> service.verify(wrongOwner));
    assertEquals(OtpStatus.ACTIVE, repository.findById(id).orElseThrow().getSnapshot().status());
    var expired =
        new OtpVerificationCommand(
            id, customer("A"), OtpPurpose.LOGIN, "123456", null, "127.0.0.1");
    assertEquals(
        OtpVerificationResult.EXPIRED,
        assertThrows(OtpVerificationException.class, () -> service.verify(expired)).result());
    assertEquals(OtpStatus.EXPIRED, repository.findById(id).orElseThrow().getSnapshot().status());
    assertEquals(0, repository.findById(id).orElseThrow().getSnapshot().attempts());
  }

  private java.util.List<OtpVerificationResult> verifyTogether(OtpId id, String code, int callers)
      throws Exception {
    var barrier = new java.util.concurrent.CyclicBarrier(callers);
    OtpRepository racing =
        new OtpRepository() {
          @Override
          public void save(OTP otp) {
            repository.save(otp);
          }

          @Override
          public void replaceActive(OTP otp) {
            repository.replaceActive(otp);
          }

          @Override
          public java.util.Optional<OTP> findById(OtpId key) {
            return repository.findById(key);
          }

          @Override
          public <T> java.util.Optional<T> withLockedChallenge(
              OtpId key, java.util.function.Function<OTP, T> mutation) {
            /* All callers have read the original snapshot before any transaction acquires its row lock. */
            await(barrier);
            return repository.withLockedChallenge(key, mutation);
          }
        };
    OtpApplicationService competing =
        new OtpApplicationService(
            racing,
            hasher,
            codes,
            (subject, value, at) -> false,
            message -> {},
            quota,
            () -> id,
            events,
            audit,
            java.time.Clock.systemUTC(),
            java.time.Duration.ofMinutes(5),
            java.time.Duration.ofSeconds(30),
            java.time.Duration.ofDays(1097),
            5);
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<OtpVerificationResult>>();
      for (int caller = 0; caller < callers; caller++)
        futures.add(
            executor.submit(
                () -> {
                  try {
                    competing.verify(
                        new OtpVerificationCommand(
                            id, customer("A"), OtpPurpose.LOGIN, code, null, "127.0.0.1"));
                    return OtpVerificationResult.VERIFIED;
                  } catch (OtpVerificationException exception) {
                    return exception.result();
                  }
                }));
      var results = new java.util.ArrayList<OtpVerificationResult>();
      for (var future : futures) results.add(future.get(15, java.util.concurrent.TimeUnit.SECONDS));
      return results;
    }
  }

  @Test
  void restoredStatesNullableTotpHashesAndCaseDistinctIdentifiersRoundTrip() {
    Instant created =
        Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(600);
    OtpStatus[] statuses = {OtpStatus.USED, OtpStatus.LOCKED, OtpStatus.EXPIRED, OtpStatus.ACTIVE};
    String[] suffixes = {"A", "a", "B", "b"};
    for (int index = 0; index < statuses.length; index++) {
      OtpStatus status = statuses[index];
      var snapshot =
          new OtpSnapshot(
              OtpPurpose.LOGIN,
              OtpChannel.AUTHENTICATOR_APP,
              OtpVerificationMode.TOTP,
              null,
              null,
              5,
              created,
              created.plusSeconds(300),
              created.plusSeconds(1097L * 86_400),
              status == OtpStatus.LOCKED ? 5 : 1,
              status,
              status == OtpStatus.USED ? created.plusSeconds(1) : null);
      OtpId id = OtpId.parse("otp_" + suffixes[index].repeat(22));
      CustomerId subject = customer(suffixes[index]);
      repository.save(OTP.restore(id, subject, snapshot));
      var restored = repository.findById(id).orElseThrow();
      assertEquals(subject, restored.getCustomerId());
      assertEquals(snapshot, restored.getSnapshot());
    }
    assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM identity_challenge", Integer.class));
  }

  @Test
  void challengeExpiringBetweenInitialReadAndLockedMutationIsRejectedAtExactBoundary() {
    OTP otp = challenge("A", customer("A"), OtpPurpose.LOGIN, hasher.hash("123456"));
    repository.save(otp);
    Instant expires = otp.getSnapshot().expiresAt();
    var reads = new java.util.concurrent.atomic.AtomicInteger();
    java.time.Clock advancing =
        new java.time.Clock() {
          @Override
          public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
          }

          @Override
          public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return reads.getAndIncrement() == 0 ? expires.minusSeconds(1) : expires;
          }
        };
    var boundary =
        new OtpApplicationService(
            repository,
            hasher,
            codes,
            (subject, code, at) -> false,
            message -> {},
            quota,
            otp::getId,
            events,
            audit,
            advancing,
            java.time.Duration.ofMinutes(5),
            java.time.Duration.ofSeconds(30),
            java.time.Duration.ofDays(1097),
            5);
    var command =
        new OtpVerificationCommand(
            otp.getId(), customer("A"), OtpPurpose.LOGIN, "123456", null, "127.0.0.1");
    assertEquals(
        OtpVerificationResult.EXPIRED,
        assertThrows(OtpVerificationException.class, () -> boundary.verify(command)).result());
    var restored = repository.findById(otp.getId()).orElseThrow().getSnapshot();
    assertEquals(OtpStatus.EXPIRED, restored.status());
    assertEquals(0, restored.attempts());
  }

  private static void await(java.util.concurrent.CyclicBarrier barrier) {
    try {
      barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Competing request did not reach its test barrier", exception);
    }
  }

  private static CustomerId customer(String suffix) {
    return CustomerId.parse("cus_" + suffix.repeat(22));
  }

  private static OTP challenge(
      String suffix, CustomerId customer, OtpPurpose purpose, String hash) {
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return OTP.issue(
        OtpId.parse("otp_" + suffix.repeat(22)),
        customer,
        new OtpSnapshot(
            purpose,
            OtpChannel.SMS,
            OtpVerificationMode.RANDOM_NUMERIC,
            hash,
            null,
            5,
            now,
            now.plusSeconds(300),
            now.plusSeconds(1097L * 86_400),
            0,
            OtpStatus.ACTIVE,
            null));
  }
}
