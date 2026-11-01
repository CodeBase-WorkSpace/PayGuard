package dev.amg.payguard.identity.verification.infrastructure.adapter.out.persistence.jooq;

import domain.DomainEvent;

/**
 * Converts a Domain Event to a versioned outbox message.
 *
 * <p>Implementations must expose only approved event fields. OTP codes, hashes, and device
 * fingerprints must not be serialized.
 */
@FunctionalInterface
public interface OtpOutboxEncoder {

  EncodedEvent encode(DomainEvent event);

  record EncodedEvent(String eventType, String json) {}
}
