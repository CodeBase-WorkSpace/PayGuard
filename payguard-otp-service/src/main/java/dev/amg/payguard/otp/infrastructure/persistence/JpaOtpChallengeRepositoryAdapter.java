package dev.amg.payguard.otp.infrastructure.persistence;

import dev.amg.payguard.otp.domain.OtpChallenge;
import dev.amg.payguard.otp.domain.OtpChallengeRepository;
import dev.amg.payguard.otp.domain.OtpChallengeStatus;
import dev.amg.payguard.otp.domain.OtpPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaOtpChallengeRepositoryAdapter implements OtpChallengeRepository {

  private final OtpChallengeJpaRepository repository;

  public JpaOtpChallengeRepositoryAdapter(OtpChallengeJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void invalidateActive(String userId, OtpPurpose purpose) {
    repository
        .findByUserIdAndPurposeAndStatus(userId, purpose, OtpChallengeStatus.ACTIVE)
        .forEach(
            entity -> {
              OtpChallenge challenge = entity.toDomain();
              challenge.invalidate();
              entity.copyFrom(challenge);
              repository.save(entity);
            });
  }

  @Override
  @Transactional
  public OtpChallenge save(OtpChallenge challenge) {
    OtpChallengeEntity entity =
        repository
            .findById(challenge.id())
            .map(
                existing -> {
                  existing.copyFrom(challenge);
                  return existing;
                })
            .orElseGet(() -> OtpChallengeEntity.from(challenge));
    return repository.save(entity).toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OtpChallenge> findById(UUID challengeId) {
    return repository.findById(challengeId).map(OtpChallengeEntity::toDomain);
  }
}
