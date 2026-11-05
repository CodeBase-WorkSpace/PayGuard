package dev.amg.payguard.identity.verification.application.port;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import java.time.Instant;

/**
 * customerId is the challenge subject, not an authenticated actor. No trusted actor or correlation
 * context is currently supplied by the inbound contract.
 */
public record VerificationAuditEntry(
        OtpId challengeId, // The generated OTP.
        CustomerId customerId, // The person that OTP generated for
        VerificationAuditAction verificationAuditAction, // What action happened to the OTP?
        String reasonCode, // Code that identifies why an OTP was requested, generated, or validated.
        String sourceIp, // IP address from which the OTP request originated.
        Instant occurredAt
) {}
