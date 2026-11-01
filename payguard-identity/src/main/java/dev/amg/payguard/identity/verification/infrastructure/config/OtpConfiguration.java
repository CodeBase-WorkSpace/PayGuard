package dev.amg.payguard.identity.verification.infrastructure.config;

import dev.amg.payguard.identity.verification.application.command.issue_otp.IssueOtpHandler;
import dev.amg.payguard.identity.verification.application.command.otp_verification.OtpVerificationHandler;
import dev.amg.payguard.identity.verification.application.port.OtpDelivery;
import dev.amg.payguard.identity.verification.application.port.VerificationAudit;
import dev.amg.payguard.identity.verification.application.port.rate_limit.RateLimitPort;
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
      } catch (infrastructure.vault.VaultUnavailableException _) {
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
  IssueOtpHandler issueOtpHandler(
      OtpRepository repository,
      OtpCodeGenerator generator,
      OtpCodeHasher hasher,
      RateLimitPort quota,
      OtpDelivery delivery,
      VerificationAudit audit,
      OtpIdGenerator ids,
      Clock identityClock,
      @Value("${identity.challenge.random-ttl:PT5M}") Duration ttl,
      @Value("${identity.audit.retention:P1097D}") Duration auditRetention,
      @Value("${identity.challenge.max-attempts:5}") int maxAttempts) {

    return new IssueOtpHandler(
        repository,
        generator,
        hasher,
        quota,
        delivery,
        audit,
        ids::generate,
        identityClock,
        ttl,
        auditRetention,
        maxAttempts);
  }

  @Bean
  OtpVerificationHandler otpVerificationHandler(
      OtpRepository repository,
      OtpCodeHasher hasher,
      RateLimitPort quota,
      VerificationAudit audit,
      Clock identityClock) {

    return new OtpVerificationHandler(repository, hasher, quota, audit, identityClock);
  }
}
