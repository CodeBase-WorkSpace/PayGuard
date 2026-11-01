package dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;

public record VerifyOtpResponse(String challengeId, OtpPurpose purpose, Instant verifiedAt) {}
