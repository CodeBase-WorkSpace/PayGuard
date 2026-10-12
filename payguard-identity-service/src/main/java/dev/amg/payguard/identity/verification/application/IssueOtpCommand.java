package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;

public record IssueOtpCommand(
    CustomerId customerId,
    OtpPurpose purpose,
    OtpChannel channel,
    String destinationRef,
    String deviceFingerprint,
    String sourceIp) {}
