package dev.amg.payguard.identity.verification.domain.model;

import dev.amg.payguard.identity.customer.domain.value_object.CustomerId;
import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationMode;
import dev.amg.payguard.identity.verification.domain.enums.OtpVerificationResult;
import dev.amg.payguard.identity.verification.domain.policy.OtpVerificationPolicy;
import dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher;
import dev.amg.payguard.identity.verification.domain.value_object.VerifyOtpCommand;
import domain.AggregateRoot;
import domain.DomainEvent;
import java.util.Objects;
import java.util.Optional;

/**
 * An OTP challenge associated with a PayGuard customer.
 *
 * <p>Owns the challenge's verification and invalidation behavior. State changes replace the current
 * {@link OtpSnapshot} without modifying previously created snapshots.
 *
 * <p>Equality and hashing use the immutable challenge identity inherited from the aggregate base.
 *
 * <h2>Issuance and restoration</h2>
 *
 * <p>Issuance requires an ACTIVE snapshot with zero recorded attempts and no consumption time.
 * Restoration accepts an existing validated snapshot without resetting its status, attempt count,
 * or timestamps.
 *
 * <h2>Verification</h2>
 *
 * <p>The verification policy checks for rejection before the code is checked. Rejected requests do
 * not increase the attempt count. If the policy reports expiration for an ACTIVE challenge, its
 * status changes to EXPIRED.
 *
 * <p>A failed code check records one attempt. The last allowed failed attempt also changes the
 * status to LOCKED in the same new snapshot.
 *
 * <p>A successful code check records one attempt, changes the status to USED, and sets the
 * consumption time to the time supplied by the command. A correct code is therefore accepted on the
 * last allowed attempt.
 *
 * <p>For TOTP verification, this aggregate uses the verification result supplied by the command; it
 * does not validate the TOTP itself. For other verification modes, it compares the submitted code
 * with the stored hash through {@link
 * dev.amg.payguard.identity.verification.domain.port.OtpCodeHasher}.
 *
 * <h2>Invalidation</h2>
 *
 * <p>Invalidation changes an ACTIVE challenge to INVALIDATED. Challenges in other states remain
 * unchanged.
 *
 * <h2>Responsibility</h2>
 *
 * <p>A VERIFIED result reports successful completion of this challenge. It does not establish
 * permission to access a wallet or execute a financial operation. The consuming application must
 * enforce those permissions and associate the result with the intended operation.
 *
 * @implSpec This aggregate is mutable and does not synchronize its operations. Concurrent mutations
 *     and persistence updates require coordination outside this class. Replacing an immutable
 *     snapshot does not make verification thread-safe.
 */
public final class OTP extends AggregateRoot<OtpId, DomainEvent> {

  private final CustomerId customerId;
  private OtpSnapshot snapshot;

  private OTP(OtpId id, CustomerId customerId, OtpSnapshot snapshot) {
    super(Objects.requireNonNull(id, "id must not be null"));
    this.customerId = Objects.requireNonNull(customerId, "customerID must not be null");
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
  }

  public static OTP issue(OtpId id, CustomerId customerId, OtpSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot must not be null");
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(customerId, "customerId must not be null");

    if (snapshot.status() != OtpStatus.ACTIVE || snapshot.attempts() != 0) {
      throw new IllegalArgumentException("issuance requires an initial snapshot");
    }

    return new OTP(id, customerId, snapshot);
  }

  public static OTP restore(OtpId id, CustomerId customerId, OtpSnapshot snapshot) {
    return new OTP(id, customerId, snapshot);
  }

  private static boolean isCodeValid(
      VerifyOtpCommand command, OtpCodeHasher hasher, OtpSnapshot snapshot) {
    if (snapshot.verificationMode() == OtpVerificationMode.TOTP) {
      return command.authenticatorCodeValid();
    }

    return snapshot.codeHash() != null && hasher.matches(command.code(), snapshot.codeHash());
  }

  public OtpVerificationResult verify(VerifyOtpCommand command, OtpCodeHasher hasher) {
    Optional<OtpVerificationResult> rejection =
        OtpVerificationPolicy.findRejection(command, snapshot, hasher);

    if (rejection.isPresent()) {
      OtpVerificationResult result = rejection.get();
      if (result == OtpVerificationResult.EXPIRED && snapshot.status() == OtpStatus.ACTIVE) {
        this.snapshot = this.snapshot.withStatus(OtpStatus.EXPIRED);
      }
      return result;
    }
    boolean codeMatches = isCodeValid(command, hasher, snapshot);

    if (!codeMatches) {
      final boolean lastAllowedAttempt = snapshot.attempts() + 1 == snapshot.maxAttempts();

      this.snapshot =
          lastAllowedAttempt ? snapshot.withFailedAttemptAndLock() : snapshot.withAttemptRecorded();

      return lastAllowedAttempt ? OtpVerificationResult.LOCKED : OtpVerificationResult.INVALID_CODE;
    }

    this.snapshot = snapshot.withSuccessfulAttempt(command.now());
    return OtpVerificationResult.VERIFIED;
  }

  public void invalidate() {
    if (snapshot.status() == OtpStatus.ACTIVE) {
      this.snapshot = this.snapshot.withStatus(OtpStatus.INVALIDATED);
    }
  }

  public CustomerId getCustomerId() {
    return customerId;
  }

  public OtpSnapshot getSnapshot() {
    return snapshot;
  }
}
