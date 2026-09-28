package dev.amg.payguard.otp.application;

import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpPurpose;
import java.time.Instant;
import java.util.UUID;

public record IssuedOtp(
    UUID challengeId, OtpPurpose purpose, OtpChannel channel, Instant expiresAt) {}
