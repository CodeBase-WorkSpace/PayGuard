package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;

public record IssuedOtp(
    OtpId challengeId, OtpPurpose purpose, OtpChannel channel, Instant expiresAt) {}
