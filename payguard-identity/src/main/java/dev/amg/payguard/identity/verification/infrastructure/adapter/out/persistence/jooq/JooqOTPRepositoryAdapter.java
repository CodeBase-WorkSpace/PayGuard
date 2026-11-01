package dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq;

import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.Tables.IDENTITY_OTP;
import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.Tables.IDENTITY_OTP_SCOPE;
import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.Tables.IDENTITY_OUTBOX;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.enums.IdentityStatus;
import dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.tables.records.IdentityOtpRecord;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import domain.DomainEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqOTPRepositoryAdapter implements OtpRepository {
  private static final int EXPECTED_AFFECTED_ROWS = 1;

  private final DSLContext dsl;
  private final Clock clock;
  private final OtpOutboxEncoder outboxEncoder;

  public JooqOTPRepositoryAdapter(DSLContext dsl, Clock clock, OtpOutboxEncoder outboxEncoder) {
    this.dsl = dsl;
    this.clock = clock;
    this.outboxEncoder = outboxEncoder;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void replaceActive(OTP replacement) {
    Objects.requireNonNull(replacement);
    String customerId = replacement.getCustomerId().getValue();
    String purpose = replacement.getSnapshot().purpose().name();

    dsl.insertInto(IDENTITY_OTP_SCOPE)
        .set(IDENTITY_OTP_SCOPE.USER_ID, customerId)
        .set(IDENTITY_OTP_SCOPE.PURPOSE, purpose)
        .onConflict(IDENTITY_OTP_SCOPE.USER_ID, IDENTITY_OTP_SCOPE.PURPOSE)
        .doNothing()
        .execute();

    final var scope =
        dsl.select(IDENTITY_OTP_SCOPE.USER_ID)
            .from(IDENTITY_OTP_SCOPE)
            .where(IDENTITY_OTP_SCOPE.USER_ID.eq(customerId))
            .and(IDENTITY_OTP_SCOPE.PURPOSE.eq(purpose))
            .forUpdate()
            .fetchOne();

    if (Objects.isNull(scope)) {
      throw new IllegalStateException("OTP issuance scope not found");
    }

    final IdentityOtpRecord previousRow =
        dsl.selectFrom(IDENTITY_OTP)
            .where(IDENTITY_OTP.USER_ID.eq(customerId))
            .and(IDENTITY_OTP.PURPOSE.eq(purpose))
            .and(IDENTITY_OTP.STATUS.eq(IdentityStatus.ACTIVE))
            .forUpdate()
            .fetchOne();

    if (!Objects.isNull(previousRow)) {
      OTP previous = restore(previousRow);

      Instant invalidatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

      previous.invalidate(invalidatedAt);

      updateState(previous);
      appendOutbox(previous);
    }

    insert(replacement);
    appendOutbox(replacement);
  }

  @Override
  public void save(OTP otp) {
    Objects.requireNonNull(otp);
    insert(otp);
    appendOutbox(otp);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> Optional<T> withLockedChallenge(OtpId id, Function<OTP, T> mutation) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(mutation);
    IdentityOtpRecord row =
        dsl.selectFrom(IDENTITY_OTP).where(IDENTITY_OTP.ID.eq(id.value())).forUpdate().fetchOne();
    if (row == null) {
      return Optional.empty();
    }
    OTP otp = restore(row);
    T result = Objects.requireNonNull(mutation.apply(otp), "OTP mutation result must not be null");
    updateState(otp);
    appendOutbox(otp);
    return Optional.of(result);
  }

  @Override
  public Optional<OTP> findById(OtpId otpId) {
    Objects.requireNonNull(otpId);

    return dsl.selectFrom(IDENTITY_OTP)
        .where(IDENTITY_OTP.ID.eq(otpId.value()))
        .fetchOptional()
        .map(JooqOTPRepositoryAdapter::restore);
  }

  private void insert(OTP otp) {
    OtpSnapshot snapshot = otp.getSnapshot();

    int inserted =
        dsl.insertInto(IDENTITY_OTP)
            .set(IDENTITY_OTP.ID, otp.getId().value())
            .set(IDENTITY_OTP.USER_ID, otp.getCustomerId().getValue())
            .set(IDENTITY_OTP.PURPOSE, snapshot.purpose().name())
            .set(IDENTITY_OTP.CHANNEL, snapshot.channel().name())
            .set(IDENTITY_OTP.VERIFICATION_MODE, snapshot.verificationMode().name())
            .set(IDENTITY_OTP.CODE_HASH, snapshot.codeHash())
            .set(IDENTITY_OTP.DEVICE_BINDING_HASH, snapshot.deviceBindingHash())
            .set(IDENTITY_OTP.ATTEMPTS, snapshot.attempts())
            .set(IDENTITY_OTP.MAX_ATTEMPTS, snapshot.maxAttempts())
            .set(IDENTITY_OTP.STATUS, databaseStatus(snapshot.status()))
            .set(IDENTITY_OTP.CREATED_AT, utc(snapshot.createdAt()))
            .set(IDENTITY_OTP.EXPIRES_AT, utc(snapshot.expiresAt()))
            .set(IDENTITY_OTP.CONSUMED_AT, nullableUtc(snapshot.consumedAt()))
            .set(IDENTITY_OTP.RETENTION_UNTIL, utc(snapshot.retentionUntil()))
            .execute();

    if (inserted != EXPECTED_AFFECTED_ROWS) {
      throw new IllegalStateException("Failed to insert OTP challenge");
    }
  }

  private void appendOutbox(OTP otp) {
    // Pull all otp related events
    for (DomainEvent event : otp.pullDomainEvents()) {
      final OtpOutboxEncoder.EncodedEvent encodedEvent = outboxEncoder.encode(event);
      dsl.insertInto(IDENTITY_OUTBOX)
          .set(IDENTITY_OUTBOX.EVENT_ID, UUID.randomUUID())
          .set(IDENTITY_OUTBOX.AGGREGATE_ID, otp.getId().value())
          .set(IDENTITY_OUTBOX.EVENT_TYPE, encodedEvent.eventType())
          .set(IDENTITY_OUTBOX.PAYLOAD, JSON.valueOf(encodedEvent.json()))
          .set(IDENTITY_OUTBOX.OCCURRED_AT, utc(event.occurredOn()))
          .execute();
    }
  }

  /**
   * Persists the mutable lifecycle fields.
   *
   * <p>All updates to an existing challenge must occur after its row has been locked by the calling
   * operation.
   */
  private void updateState(OTP otp) {
    OtpSnapshot snapshot = otp.getSnapshot();

    int updated =
        dsl.update(IDENTITY_OTP)
            .set(IDENTITY_OTP.STATUS, databaseStatus(snapshot.status()))
            .set(IDENTITY_OTP.ATTEMPTS, snapshot.attempts())
            .set(IDENTITY_OTP.CONSUMED_AT, nullableUtc(snapshot.consumedAt()))
            .where(IDENTITY_OTP.ID.eq(otp.getId().value()))
            .execute();

    if (updated != EXPECTED_AFFECTED_ROWS) {
      throw new IllegalStateException("Failed to update OTP challenge");
    }
  }

  private static IdentityStatus databaseStatus(OtpStatus status) {
    return IdentityStatus.valueOf(status.name());
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static OffsetDateTime nullableUtc(Instant instant) {
    return instant == null ? null : utc(instant);
  }

  private static OTP restore(IdentityOtpRecord row) {
    final OtpSnapshot snapshot =
        OtpSnapshot.create(
            OtpPurpose.valueOf(row.get(IDENTITY_OTP.PURPOSE)),
            OtpChannel.valueOf(row.get(IDENTITY_OTP.CHANNEL)),
            OtpVerificationMode.valueOf(row.get(IDENTITY_OTP.VERIFICATION_MODE)),
            row.get(IDENTITY_OTP.CODE_HASH),
            row.get(IDENTITY_OTP.DEVICE_BINDING_HASH),
            row.get(IDENTITY_OTP.MAX_ATTEMPTS),
            row.get(IDENTITY_OTP.CREATED_AT).toInstant(),
            row.get(IDENTITY_OTP.EXPIRES_AT).toInstant(),
            row.get(IDENTITY_OTP.RETENTION_UNTIL).toInstant(),
            row.get(IDENTITY_OTP.ATTEMPTS),
            OtpStatus.valueOf(row.get(IDENTITY_OTP.STATUS).getLiteral()),
            row.get(IDENTITY_OTP.CONSUMED_AT) == null
                ? null
                : row.get(IDENTITY_OTP.CONSUMED_AT).toInstant());
    return OTP.restore(
        OtpId.parse(row.get(IDENTITY_OTP.ID)),
        CustomerId.parse(row.get(IDENTITY_OTP.USER_ID)),
        snapshot);
  }
}
