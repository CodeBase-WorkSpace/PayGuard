package dev.amg.payguard.identity.verification.infrastructure.security;

import infrastructure.vault.VaultClient;
import infrastructure.vault.VaultUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Refreshes keys outside persistence locks. A removed key is rejected during the configured OTP
 * compatibility window. Cached keys have a bounded outage lifetime; no default key is supplied.
 */
@Component
@ConditionalOnProperty(name = "payguard.vault.enabled", havingValue = "true")
public final class VaultPepperKeys implements Supplier<PepperKeyRing>, AutoCloseable {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(VaultPepperKeys.class);
  private final VaultClient vault;
  private final String prefix;
  private final Duration compatibility;
  private final Map<String, Instant> lastUsed = new HashMap<>();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("otp-key-refresh").factory());
  private volatile PepperKeyRing ring;
  private volatile Instant fetchedAt;
  private volatile boolean latestRefreshSucceeded = true;

  public VaultPepperKeys(
      VaultClient vault,
      @Value("${identity.challenge.random-ttl:PT5M}") Duration randomTtl,
      @Value("${identity.challenge.totp-ttl:PT30S}") Duration totpTtl,
      @Value("${payguard.vault.secret-prefix}") String prefix) {
    this.vault = vault;
    if (!prefix.matches("[a-z][a-z0-9-]*/identity")) {
      throw new IllegalArgumentException("Invalid Identity secret scope");
    }
    this.prefix = prefix;
    compatibility = randomTtl.compareTo(totpTtl) >= 0 ? randomTtl : totpTtl;
    ring = fetch();
    fetchedAt = Instant.now();
    ring.keys().keySet().forEach(key -> lastUsed.put(key, fetchedAt));
    scheduler.scheduleWithFixedDelay(this::refresh, 5, 5, TimeUnit.SECONDS);
  }

  private PepperKeyRing fetch() {
    try {
      var data = vault.read("secret/data/" + prefix + "/otp-keys").path("data").path("data");
      Map<String, String> keys = new HashMap<>();
      data.path("keys")
          .properties()
          .forEach(entry -> keys.put(entry.getKey(), entry.getValue().asText()));
      String legacy = data.path("legacy").asText();
      return new PepperKeyRing(
          data.path("current").asText(), keys, legacy.isBlank() ? null : legacy);
    } catch (RuntimeException exception) {
      throw new VaultUnavailableException(exception);
    }
  }

  private synchronized void refresh() {
    try {
      PepperKeyRing next = fetch();
      Instant now = Instant.now();
      lastUsed.put(ring.current(), now);
      for (var entry : ring.keys().entrySet()) {
        String nextValue = next.keys().get(entry.getKey());
        if (nextValue != null && !entry.getValue().equals(nextValue)) {
          throw new VaultUnavailableException();
        }
        Instant last = lastUsed.getOrDefault(entry.getKey(), fetchedAt);
        if (nextValue == null && now.isBefore(last.plus(compatibility).plusSeconds(10))) {
          throw new VaultUnavailableException();
        }
      }
      validateLegacySelection(next, now);
      if (!ring.current().equals(next.current())) {
        lastUsed.put(ring.current(), now);
      }
      ring = next;
      fetchedAt = now;
      latestRefreshSucceeded = true;
    } catch (VaultUnavailableException exception) {
      /* Retain valid cached keys temporarily. A prolonged failure makes get() fail closed. */
      if (latestRefreshSucceeded)
        LOG.warn("OTP key refresh unavailable; bounded cached keys retained");
      latestRefreshSucceeded = false;
    }
  }

  private void validateLegacySelection(PepperKeyRing next, Instant now) {
    String legacy = ring.legacy();
    if (legacy != null && !legacy.equals(next.legacy())) {
      Instant last = lastUsed.getOrDefault(legacy, fetchedAt);
      if (next.legacy() != null || now.isBefore(last.plus(compatibility).plusSeconds(10))) {
        /* Unversioned hashes cannot identify another key; never guess a replacement legacy key. */
        throw new VaultUnavailableException();
      }
    }
  }

  @Override
  public PepperKeyRing get() {
    if (Instant.now().isAfter(fetchedAt.plus(Duration.ofMinutes(5)))) {
      throw new VaultUnavailableException();
    }
    return ring;
  }

  @Override
  public void close() {
    scheduler.shutdownNow();
  }

  public boolean refreshHealthy() {
    return latestRefreshSucceeded;
  }
}
