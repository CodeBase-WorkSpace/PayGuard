package infrastructure.vault;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Infrastructure-only composition shared by the four database-owning services. */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnProperty(name = "payguard.vault.enabled", havingValue = "true")
public class VaultDatabaseAutoConfiguration {
  @Bean(destroyMethod = "close")
  VaultClient vaultClient(Environment environment) {
    return new VaultClient(
        URI.create(environment.getRequiredProperty("payguard.vault.address")),
        Path.of(environment.getRequiredProperty("payguard.vault.ca-file")),
        Path.of(environment.getRequiredProperty("payguard.vault.role-id-file")),
        Path.of(environment.getRequiredProperty("payguard.vault.secret-id-file")));
  }

  @Bean(destroyMethod = "close")
  LeasedDataSource dataSource(VaultClient vault, Environment environment) {
    return new LeasedDataSource(
        vault,
        environment.getRequiredProperty("payguard.vault.database-role"),
        environment.getRequiredProperty("spring.datasource.url"),
        Duration.ofSeconds(10));
  }

  @Bean
  HealthIndicator vaultHealthIndicator(VaultClient vault) {
    return () -> {
      try {
        vault.read("auth/token/lookup-self");
        return Health.up().build();
      } catch (VaultUnavailableException exception) {
        return Health.down().build();
      }
    };
  }
}
