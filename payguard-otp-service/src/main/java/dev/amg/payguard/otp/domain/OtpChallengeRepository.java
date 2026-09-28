package dev.amg.payguard.otp.domain;

import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository {

  void invalidateActive(String userId, OtpPurpose purpose);

  OtpChallenge save(OtpChallenge challenge);

  Optional<OtpChallenge> findById(UUID challengeId);
}
