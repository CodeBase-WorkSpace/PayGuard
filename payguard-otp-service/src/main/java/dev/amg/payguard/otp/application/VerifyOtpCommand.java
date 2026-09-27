package dev.amg.payguard.otp.application;

import dev.amg.payguard.otp.domain.OtpPurpose;
import java.util.UUID;

public record VerifyOtpCommand(
    UUID challengeId,
    String userId,
    OtpPurpose purpose,
    String code,
    String deviceFingerprint,
    String sourceIp) {}
