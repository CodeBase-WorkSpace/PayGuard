package dev.amg.payguard.identity.verification.infrastructure.adapter.out.kafka.contracts;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Wire contract for OTP events on the identity topic (schema version 1).
 *
 * <p>This is a public contract: do not rename or remove fields. Additive changes only; breaking
 * changes require a new class (OtpEventV2) and a new topic version.
 */
@JsonInclude
public record OtpEventV1(
        String eventId,
        int schemaVersion,
        String type,
        String challengeId,
        String customerId,
        String purpose,
        String channel,
        String result,
        String occurredAt, // ISO-8601 UTC string, independent of any ObjectMapper date setting
        String reason) {

    public static final int SCHEMA_VERSION = 1;

}
