package dev.amg.payguard.identity.verification.domain.enums;

import dev.amg.payguard.identity.verification.domain.exception.OtpAttemptCountOutOfRangeException;
import dev.amg.payguard.identity.verification.domain.exception.OtpConsumedTimeMustMatchUsedStatusException;
import dev.amg.payguard.identity.verification.domain.model.OtpSnapshot;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public sealed interface OtpStatus
    permits OtpStatus.Active,
        OtpStatus.Used,
        OtpStatus.Expired,
        OtpStatus.Locked,
        OtpStatus.Invalidated {
  OtpStatus ACTIVE = new Active();
  OtpStatus USED = new Used();
  OtpStatus EXPIRED = new Expired();
  OtpStatus LOCKED = new Locked();
  OtpStatus INVALIDATED = new Invalidated();

  String name();

  Optional<OtpVerificationResult> rejection(
      VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher);

  default boolean canIssue() {
    return false;
  }

  default boolean canInvalidate() {
    return false;
  }

  default boolean expiresOnRejection() {
    return false;
  }

  default void validateAttempts(int attempts, int maxAttempts) {}

  default void validateConsumedAt(
      @Nullable Instant consumedAt, Instant createdAt, Instant expiresAt) {
    if (consumedAt != null) {
      throw new OtpConsumedTimeMustMatchUsedStatusException(consumedAt, createdAt, expiresAt);
    }
  }

  static OtpStatus valueOf(String name) {
    return switch (name) {
      case "ACTIVE" -> ACTIVE;
      case "USED" -> USED;
      case "EXPIRED" -> EXPIRED;
      case "LOCKED" -> LOCKED;
      case "INVALIDATED" -> INVALIDATED;
      default -> throw new IllegalArgumentException("Unknown OTP status: " + name);
    };
  }

  final class Active implements OtpStatus {
    private Active() {}

    @Override
    public String name() {
      return "ACTIVE";
    }

    @Override
    public boolean canIssue() {
      return true;
    }

    @Override
    public boolean canInvalidate() {
      return true;
    }

    @Override
    public boolean expiresOnRejection() {
      return true;
    }

    @Override
    public void validateAttempts(int attempts, int maxAttempts) {
      if (attempts == maxAttempts) {
        throw new OtpAttemptCountOutOfRangeException(attempts, maxAttempts);
      }
    }

    @Override
    public Optional<OtpVerificationResult> rejection(
        VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
      if (command.now().isBefore(snapshot.createdAt())) {
        throw new IllegalArgumentException("verification time must not precede challenge creation");
      }
      if (!command.now().isBefore(snapshot.expiresAt())) {
        return Optional.of(OtpVerificationResult.EXPIRED);
      }
      if (snapshot.deviceBindingHash() != null
          && (command.deviceFingerprint() == null
              || !hasher.matches(command.deviceFingerprint(), snapshot.deviceBindingHash()))) {
        return Optional.of(OtpVerificationResult.DEVICE_MISMATCH);
      }
      return Optional.empty();
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Used implements OtpStatus {
    private Used() {}

    @Override
    public String name() {
      return "USED";
    }

    @Override
    public Optional<OtpVerificationResult> rejection(
        VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
      return Optional.of(OtpVerificationResult.ALREADY_USED);
    }

    @Override
    public void validateConsumedAt(
        @Nullable Instant consumedAt, Instant createdAt, Instant expiresAt) {
      if (consumedAt == null) {
        throw new OtpConsumedTimeMustMatchUsedStatusException(consumedAt, createdAt, expiresAt);
      }
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Expired implements OtpStatus {
    private Expired() {}

    @Override
    public String name() {
      return "EXPIRED";
    }

    @Override
    public Optional<OtpVerificationResult> rejection(
        VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
      return Optional.of(OtpVerificationResult.EXPIRED);
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Locked implements OtpStatus {
    private Locked() {}

    @Override
    public String name() {
      return "LOCKED";
    }

    @Override
    public Optional<OtpVerificationResult> rejection(
        VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
      return Optional.of(OtpVerificationResult.LOCKED);
    }

    @Override
    public String toString() {
      return name();
    }
  }

  final class Invalidated implements OtpStatus {
    private Invalidated() {}

    @Override
    public String name() {
      return "INVALIDATED";
    }

    @Override
    public Optional<OtpVerificationResult> rejection(
        VerifyOtpCommand command, OtpSnapshot snapshot, OtpCodeHasher hasher) {
      return Optional.of(OtpVerificationResult.INVALIDATED);
    }

    @Override
    public String toString() {
      return name();
    }
  }
}
