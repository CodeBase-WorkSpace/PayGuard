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
   * Locks and restores current persisted state, applies one local mutation, and commits before
   * returning its result. The callback must not perform remote calls or throw a business rejection
   * whose state transition should be committed. Storage failures roll back the mutation.
   */
  <T> Optional<T> withLockedChallenge(OtpId id, Function<OTP, T> mutation);

  Optional<OTP> findById(OtpId otpId);
}
