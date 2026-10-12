package dev.amg.payguard.identity.verification.application;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;

/** Completion result, not a bearer credential or authorization for a financial operation. */
public record VerifiedOtp(OtpId challengeId, OtpPurpose purpose, Instant verifiedAt) {}
