package dev.amg.payguard.identity.verification.infrastructure.adapter.out.kafka;

import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.tables.IdentityOutbox.IDENTITY_OUTBOX;
import static org.jooq.impl.DSL.currentOffsetDateTime;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.val;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.impl.SQLDataType;
import org.jooq.types.DayToSecond;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes committed Identity outbox events to Kafka.
 *
 * <p>Events are written to PostgreSQL by the originating business
 * transaction. This relay claims eligible events, publishes them
 * to Kafka, and marks them as published after broker acknowledgment.
 *
 * <p>Each claim uses a database-backed lease and PostgreSQL's
 * {@code FOR UPDATE SKIP LOCKED} to support concurrent relay instances
 * without intentionally processing the same row simultaneously.
 *
 * <p>Delivery is at least once, not exactly once. Consumers must
 * deduplicate events by their stable event ID.
 *
 * <p>Kafka operations are never performed inside a database transaction.
 * Failed or interrupted deliveries remain recoverable from the outbox.
 *
 * <p>Requires the {@code claimed_by} and {@code claim_until} columns
 * in {@code payguard.identity_outbox}.
 */
@Component
public final class IdentityOutboxRelay {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IdentityOutboxRelay.class);

    private static final int MAX_RETRY_DELAY_SECONDS = 300;

    private static final Field<String> PAYLOAD_TEXT =
            IDENTITY_OUTBOX.PAYLOAD.cast(SQLDataType.CLOB).as("payload");

    private final DSLContext dsl;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final Duration leaseDuration;
    private final Duration sendTimeout;
    private final int maxEventsPerPoll;

    public IdentityOutboxRelay(
            DSLContext dsl,
            KafkaTemplate<String, String> kafka,
            @Value("${identity.kafka.topic:identity.events.v1}")
            String topic,
            @Value("${identity.kafka.outbox.lease:PT2M}")
            Duration leaseDuration,
            @Value("${identity.kafka.outbox.send-timeout:PT10S}")
            Duration sendTimeout,
            @Value("${identity.kafka.outbox.max-events-per-poll:50}")
            int maxEventsPerPoll) {

        this.dsl = Objects.requireNonNull(dsl);
        this.kafka = Objects.requireNonNull(kafka);
        this.topic = Objects.requireNonNull(topic);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        this.sendTimeout = Objects.requireNonNull(sendTimeout);

        if (topic.isBlank()) {
            throw new IllegalArgumentException("Kafka topic must not be blank");
        }
        if (sendTimeout.toMillis() <= 0) {
            throw new IllegalArgumentException("Send timeout must be positive");
        }
        if (leaseDuration.compareTo(sendTimeout) <= 0) {
            throw new IllegalArgumentException(
                    "Lease duration must exceed send timeout");
        }
        if (maxEventsPerPoll <= 0) {
            throw new IllegalArgumentException(
                    "Maximum events per poll must be positive");
        }

        this.maxEventsPerPoll = maxEventsPerPoll;
    }

    @Scheduled(
            fixedDelayString = "${identity.kafka.outbox.poll-delay-ms:1000}")
    public void publishPending() {

        for (int index = 0; index < maxEventsPerPoll; index++) {

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            ClaimedEvent event = claimNext();

            if (event == null) {
                return;
            }

            if (!publish(event)) {
                return;
            }
        }
    }

    /**
     * Atomically claims the next eligible outbox event.
     *
     * <p>A CTE locks one candidate using {@code SKIP LOCKED}; the
     * {@code UPDATE ... FROM} then assigns the claim token and lease.
     * The token acts as a fencing value for later updates.
     */
    private ClaimedEvent claimNext() {

        UUID claimToken = UUID.randomUUID();

        CommonTableExpression<Record1<UUID>> nextEvent =
                name("next_event").as(
                        select(IDENTITY_OUTBOX.EVENT_ID)
                                .from(IDENTITY_OUTBOX)
                                .where(IDENTITY_OUTBOX.PUBLISHED_AT.isNull())
                                .and(IDENTITY_OUTBOX.NEXT_ATTEMPT_AT
                                        .le(currentOffsetDateTime()))
                                .and(
                                        IDENTITY_OUTBOX.CLAIM_UNTIL.isNull()
                                                .or(IDENTITY_OUTBOX.CLAIM_UNTIL
                                                        .le(currentOffsetDateTime())))
                                .orderBy(
                                        IDENTITY_OUTBOX.CREATED_AT,
                                        IDENTITY_OUTBOX.EVENT_ID)
                                .limit(1)
                                .forUpdate()
                                .skipLocked());

        Field<UUID> nextEventId = nextEvent.field(IDENTITY_OUTBOX.EVENT_ID);

        Record record =
                dsl.with(nextEvent)
                        .update(IDENTITY_OUTBOX)
                        .set(IDENTITY_OUTBOX.CLAIMED_BY, claimToken)
                        .set(IDENTITY_OUTBOX.CLAIM_UNTIL, after(leaseDuration))
                        .from(nextEvent)
                        .where(IDENTITY_OUTBOX.EVENT_ID.eq(nextEventId))
                        .returning(
                                IDENTITY_OUTBOX.EVENT_ID,
                                IDENTITY_OUTBOX.AGGREGATE_ID,
                                IDENTITY_OUTBOX.EVENT_TYPE,
                                PAYLOAD_TEXT,
                                IDENTITY_OUTBOX.OCCURRED_AT,
                                IDENTITY_OUTBOX.ATTEMPT_COUNT)
                        .fetchOne();

        if (record == null) {
            return null;
        }

        return new ClaimedEvent(
                record.get(IDENTITY_OUTBOX.EVENT_ID),
                claimToken,
                record.get(IDENTITY_OUTBOX.AGGREGATE_ID),
                record.get(IDENTITY_OUTBOX.EVENT_TYPE),
                record.get(PAYLOAD_TEXT),
                record.get(IDENTITY_OUTBOX.OCCURRED_AT),
                record.get(IDENTITY_OUTBOX.ATTEMPT_COUNT));
    }

    private boolean publish(ClaimedEvent event) {

        ProducerRecord<String, String> message =
                new ProducerRecord<>(topic, event.aggregateId(), event.payload());

        message.headers()
                .add("event-id", utf8(event.eventId().toString()))
                .add("event-type", utf8(event.eventType()))
                .add("occurred-on",
                        utf8(event.occurredAt().toInstant().toString()));

        try {
            kafka.send(message)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn(
                    "Interrupted while publishing outbox event {}",
                    event.eventId());
            // The existing lease will expire and permit recovery.
            return false;

        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            scheduleRetry(event, exception);
            return false;
        }

        markPublished(event);
        return true;
    }

    /** Marks an acknowledged event as published, guarded by the claim token. */
    private void markPublished(ClaimedEvent event) {

        int updated =
                dsl.update(IDENTITY_OUTBOX)
                        .set(IDENTITY_OUTBOX.PUBLISHED_AT, currentOffsetDateTime())
                        .setNull(IDENTITY_OUTBOX.CLAIMED_BY)
                        .setNull(IDENTITY_OUTBOX.CLAIM_UNTIL)
                        .where(IDENTITY_OUTBOX.EVENT_ID.eq(event.eventId()))
                        .and(IDENTITY_OUTBOX.CLAIMED_BY.eq(event.claimToken()))
                        .and(IDENTITY_OUTBOX.PUBLISHED_AT.isNull())
                        .execute();

        if (updated == 0) {
            LOGGER.warn(
                    "Outbox event {} was acknowledged by Kafka, "
                            + "but its claim was superseded",
                    event.eventId());
            return;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "Unexpected publication update count: " + updated);
        }

        LOGGER.debug("Published outbox event {} to Kafka", event.eventId());
    }

    /** Schedules a retry with exponential backoff, guarded by the claim token. */
    private void scheduleRetry(ClaimedEvent event, Exception failure) {

        int exponent = (int) Math.min(9L, (long) event.attemptCount() + 1L);
        long delaySeconds = Math.min(MAX_RETRY_DELAY_SECONDS, 1L << exponent);

        int updated =
                dsl.update(IDENTITY_OUTBOX)
                        .set(IDENTITY_OUTBOX.ATTEMPT_COUNT,
                                IDENTITY_OUTBOX.ATTEMPT_COUNT.plus(1))
                        .set(IDENTITY_OUTBOX.NEXT_ATTEMPT_AT,
                                after(Duration.ofSeconds(delaySeconds)))
                        .setNull(IDENTITY_OUTBOX.CLAIMED_BY)
                        .setNull(IDENTITY_OUTBOX.CLAIM_UNTIL)
                        .where(IDENTITY_OUTBOX.EVENT_ID.eq(event.eventId()))
                        .and(IDENTITY_OUTBOX.CLAIMED_BY.eq(event.claimToken()))
                        .and(IDENTITY_OUTBOX.PUBLISHED_AT.isNull())
                        .execute();

        if (updated == 0) {
            LOGGER.warn(
                    "Could not schedule retry for outbox event {} "
                            + "because claim ownership was lost",
                    event.eventId());
            return;
        }

        LOGGER.warn(
                "Kafka publication failed for outbox event {}. "
                        + "Retry scheduled after {} seconds",
                event.eventId(),
                delaySeconds,
                failure);
    }

    /** Database-side "now + duration", evaluated on the DB clock. */
    private static Field<OffsetDateTime> after(Duration duration) {
        return currentOffsetDateTime()
                .add(val(DayToSecond.valueOf(duration)));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record ClaimedEvent(
            UUID eventId,
            UUID claimToken,
            String aggregateId,
            String eventType,
            String payload,
            OffsetDateTime occurredAt,
            int attemptCount
    ) {}
}