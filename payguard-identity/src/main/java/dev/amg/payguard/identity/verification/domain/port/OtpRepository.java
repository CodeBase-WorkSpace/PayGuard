package dev.amg.payguard.identity.verification.domain.port;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import java.util.Optional;
import java.util.function.Function;

public interface OtpRepository {
  /**
   * Invalidates ACTIVE challenges for the same customer and purpose and saves the new challenge in
   * one database transaction, serialized for the same customer and purpose even on first issuance.
   */
  void replaceActive(OTP otp);

  /**
   * Inserts a challenge. Existing challenges must be mutated through the locked update contract.
   */
  void save(OTP otp);

  /**
   * Locks and restores the current persisted challenge, applies a mutation, and persists the
   * resulting state within the caller's transaction.
   *
   * <p>The database row lock must remain held until the caller's transaction commits or rolls back.
   *
   * <p>The callback must not perform remote calls. Business rejections that modify challenge state
   * must be returned as results rather than thrown.
   *
   * @param id the challenge identifier
   * @param mutation the operation applied to the locked aggregate
   * @param <T> the mutation result type
   * @return the mutation result, or empty if the challenge does not exist
   */
  <T> Optional<T> withLockedChallenge(OtpId id, Function<OTP, T> mutation);

  Optional<OTP> findById(OtpId otpId);
}
