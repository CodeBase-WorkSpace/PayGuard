package dev.amg.payguard.otp.application;

import dev.amg.payguard.otp.domain.OtpPurpose;
import java.time.Instant;

public record VerifiedOtp(String stepUpToken, OtpPurpose purpose, Instant expiresAt) {}
