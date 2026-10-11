package dev.amg.payguard.identity.verification.infrastructure.config;

import dev.amg.payguard.identity.verification.application.OtpApplicationService;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.TotpVerification;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.VerificationQuota;
import dev.amg.payguard.identity.verification.domain.event.OtpEventPublisher;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeGenerator;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import dev.amg.payguard.identity.verification.infrastructure.security.OtpIdGenerator;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpConfiguration {

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "payguard.vault.enabled",
      havingValue = "true")
  org.springframework.boot.health.contributor.HealthIndicator otpKeysHealthIndicator(
      dev.amg.payguard.identity.verification.infrastructure.security.VaultPepperKeys keys) {
    return () -> {
      try {
        keys.get();
        return (keys.refreshHealthy()
                ? org.springframework.boot.health.contributor.Health.up()
                : org.springframework.boot.health.contributor.Health.down())
            .build();
      } catch (infrastructure.vault.VaultUnavailableException exception) {
        return org.springframework.boot.health.contributor.Health.down().build();
      }
    };
  }

  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "payguard.vault.enabled",
      havingValue = "true")
  dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher vaultOtpHasher(
      dev.amg.payguard.identity.verification.infrastructure.security.VaultPepperKeys keys) {
    return new dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher(
        keys);
  }

  @Bean
  @org.springframework.context.annotation.Profile("test")
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      name = "payguard.vault.enabled",
      havingValue = "false")
  dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher
      syntheticOtpHasher() {
    return new dev.amg.payguard.identity.verification.infrastructure.security.HmacOtpCodeHasher(
        "synthetic-test-pepper-at-least-32-characters");
  }

  @Bean
  Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  OtpApplicationService identityApplicationService(
      OtpRepository challenges,
      OtpCodeHasher hasher,
      OtpCodeGenerator generator,
      TotpVerification authenticatorCodeVerifier,
      OtpDelivery delivery,
      VerificationQuota rateLimiter,
      OtpIdGenerator ids,
      OtpEventPublisher events,
      VerificationAudit audit,
      Clock identityClock,
      @Value("${identity.challenge.random-ttl:PT5M}") Duration randomTtl,
      @Value("${identity.challenge.totp-ttl:PT30S}") Duration totpTtl,
      @Value("${identity.audit.retention:P1097D}") Duration auditRetention,
      @Value("${identity.challenge.max-attempts:5}") int maxAttempts) {
    return new OtpApplicationService(
        challenges,
        hasher,
        generator,
        authenticatorCodeVerifier,
        delivery,
        rateLimiter,
        ids::generate,
        events,
        audit,
        identityClock,
        randomTtl,
        totpTtl,
        auditRetention,
        maxAttempts);
  }
}
