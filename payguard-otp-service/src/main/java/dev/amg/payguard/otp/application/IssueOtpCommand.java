package dev.amg.payguard.otp.application;

import dev.amg.payguard.otp.domain.OtpChannel;
import dev.amg.payguard.otp.domain.OtpPurpose;

public record IssueOtpCommand(
    String userId,
    OtpPurpose purpose,
    OtpChannel channel,
    String destinationRef,
    String deviceFingerprint,
    String sourceIp) {}
