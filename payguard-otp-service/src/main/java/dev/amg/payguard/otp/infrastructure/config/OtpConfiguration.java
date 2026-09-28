package dev.amg.payguard.otp.infrastructure.config;

import dev.amg.payguard.otp.application.OtpApplicationService;
import dev.amg.payguard.otp.domain.AuthenticatorCodeVerifier;
import dev.amg.payguard.otp.domain.OtpAuditLogPort;
import dev.amg.payguard.otp.domain.OtpChallengeRepository;
import dev.amg.payguard.otp.domain.OtpCodeGenerator;
import dev.amg.payguard.otp.domain.OtpCodeHasher;
import dev.amg.payguard.otp.domain.OtpEventPublisher;
import dev.amg.payguard.otp.domain.RateLimiter;
import dev.amg.payguard.otp.domain.StepUpTokenPort;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpConfiguration {

  @Bean
  Clock otpClock() {
    return Clock.systemUTC();
  }

  @Bean
  OtpApplicationService otpApplicationService(
      OtpChallengeRepository challenges,
      OtpCodeHasher hasher,
      OtpCodeGenerator generator,
      AuthenticatorCodeVerifier authenticatorCodeVerifier,
      dev.amg.payguard.otp.domain.DeliveryPort delivery,
      RateLimiter rateLimiter,
      StepUpTokenPort stepUpTokens,
      OtpEventPublisher events,
      OtpAuditLogPort audit,
      Clock otpClock,
      @Value("${otp.challenge.random-ttl:PT5M}") Duration randomTtl,
      @Value("${otp.challenge.totp-ttl:PT30S}") Duration totpTtl,
      @Value("${otp.audit.retention:PT2160H}") Duration auditRetention,
      @Value("${otp.challenge.max-attempts:5}") int maxAttempts) {
    return new OtpApplicationService(
        challenges,
        hasher,
        generator,
        authenticatorCodeVerifier,
        delivery,
        rateLimiter,
        stepUpTokens,
        events,
        audit,
        otpClock,
        randomTtl,
        totpTtl,
        auditRetention,
        maxAttempts);
  }
}
