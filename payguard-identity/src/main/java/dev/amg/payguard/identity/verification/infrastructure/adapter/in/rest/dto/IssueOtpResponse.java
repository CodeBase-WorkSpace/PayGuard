package dev.amg.payguard.identity.verification.infrastructure.adapter.in.rest.dto;

import dev.amg.payguard.identity.verification.domain.enums.OtpChannel;
import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.time.Instant;

public record IssueOtpResponse(
    String challengeId, OtpPurpose purpose, OtpChannel channel, Instant expiresAt) {}
