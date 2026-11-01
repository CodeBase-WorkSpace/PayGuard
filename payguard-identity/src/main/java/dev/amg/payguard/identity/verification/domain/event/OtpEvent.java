package dev.amg.payguard.identity.verification.domain.event;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import java.time.Instant;

public record OtpEvent(
    String type,
    OtpId challengeId,
    CustomerId customerId,
    OtpPurpose purpose,
    OtpChannel channel,
    OtpVerificationResult result,
    Instant occurredAt,
    String reason) {}
