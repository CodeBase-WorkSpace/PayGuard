package dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq;

import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationAuditEntry;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import static dev.amg.payguard.identity.infrastructure.adapter.out.persistence.jooq.generated.tables.IdentityOutbox.IDENTITY_OUTBOX;

@Repository
public class JooqOtpAuditOutboxAdapter implements VerificationAudit {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final DSLContext dsl;

    public JooqOtpAuditOutboxAdapter(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAudit(VerificationAuditEntry entry) {
        Objects.requireNonNull(entry);
        final String payload;

        try {
            final AuditPayload audit = new AuditPayload(
                    entry.challengeId().value(),
                    entry.customerId().getValue(),
                    entry.verificationAuditAction().name(),
                    entry.reasonCode(),
                    entry.sourceIp(),
                    entry.occurredAt()
            );
            payload = MAPPER.writeValueAsString(audit);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize OTP audit event", exception
            );
        }

        int inserted = dsl.insertInto(IDENTITY_OUTBOX)
                .set(IDENTITY_OUTBOX.EVENT_ID, UUID.randomUUID())
                .set(
                        IDENTITY_OUTBOX.AGGREGATE_ID,
                        entry.challengeId().value()
                )
                .set(
                        IDENTITY_OUTBOX.EVENT_TYPE,
                        "OtpAuditRecordedV1"
                )
                .set(
                        IDENTITY_OUTBOX.PAYLOAD,
                        JSON.valueOf(payload)
                )
                .set(
                        IDENTITY_OUTBOX.OCCURRED_AT,
                        entry.occurredAt().atOffset(ZoneOffset.UTC)
                )
                .execute();

        if (inserted != 1) {
            throw new IllegalStateException(
                    "Failed to persist OTP audit outbox event"
            );
        }
    }

    private record AuditPayload(
            String challengeId,
            String customerId,
            String action,
            String reasonCode,
            String sourceIp,
            Instant occurredAt
    ) {
    }
}
