package dev.amg.payguard.identity.verification.application.port.rate_limit;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents an OTP request subject to rate limiting.
 *
 * <p>Identity values must be resolved from trusted application context. Client-supplied identifiers
 * must not be treated as authenticated identities.
 *
 * @param operation the OTP operation being performed
 * @param customerId authenticated customer identifier, if available
 * @param recipientFingerprint privacy-preserving recipient identifier, if available
 * @param ipAddress trusted client IP address
 * @param purpose OTP purpose used to scope customer and recipient limits
 */
public record RateLimitRequest(
    RateLimitOperation operation,
    @Nullable String customerId,
    @Nullable String recipientFingerprint,
    String ipAddress,
    OtpPurpose purpose) {

  /**
   * Validates the request's required attributes.
   *
   * @throws NullPointerException if a required attribute is null
   * @throws IllegalArgumentException if an identifier is blank
   */
  public RateLimitRequest {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(ipAddress, "ipAddress");
    Objects.requireNonNull(purpose, "purpose");

    if (ipAddress.isBlank()) {
      throw new IllegalArgumentException("IP address must not be blank");
    }

    if (customerId != null && customerId.isBlank()) {
      throw new IllegalArgumentException("Customer ID must not be blank when provided");
    }

    if (recipientFingerprint != null && recipientFingerprint.isBlank()) {
      throw new IllegalArgumentException("Recipient fingerprint must not be blank when provided");
    }

    if (customerId == null && recipientFingerprint == null) {
      throw new IllegalArgumentException("Either customer ID or recipient fingerprint is required");
    }
  }
}
