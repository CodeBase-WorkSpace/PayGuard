package dev.amg.payguard.identity.verification.infrastructure.persistence;

import dev.amg.payguard.identity.customer.domain.value_object.OtpId;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import dev.amg.payguard.identity.verification.domain.model.OTP;
import dev.amg.payguard.identity.verification.domain.port.OtpRepository;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaOTPRepositoryAdapter implements OtpRepository {

  private final OtpChallengeJpaRepository repository;
  private final JdbcTemplate jdbc;

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public JpaOTPRepositoryAdapter(OtpChallengeJpaRepository repository, JdbcTemplate jdbc) {
    this.repository = repository;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void replaceActive(OTP replacement) {
    String customer = replacement.getCustomerId().getValue();
    String purpose = replacement.getSnapshot().purpose().name();
    jdbc.update(
        "INSERT INTO identity_challenge_scope(user_id,purpose) VALUES (?,?) ON CONFLICT DO NOTHING",
        customer,
        purpose);
    jdbc.queryForObject(
        "SELECT user_id FROM identity_challenge_scope WHERE user_id=? AND purpose=? FOR UPDATE",
        String.class,
        customer,
        purpose);
    repository
        .findByUserIdAndPurposeAndStatus(
            replacement.getCustomerId().getValue(),
            replacement.getSnapshot().purpose(),
            OtpStatus.ACTIVE)
        .forEach(
            entity -> {
              OTP challenge = entity.toDomain();
              challenge.invalidate();
              entity.copyFrom(challenge);
              repository.save(entity);
            });
    repository.flush();
    entityManager.persist(OtpChallengeEntity.from(replacement));
    entityManager.flush();
  }

  @Override
  @Transactional
  public void save(OTP challenge) {
    if (repository.existsById(challenge.getId().value())) {
      throw new IllegalStateException("Existing challenges require a locked mutation");
    }
    entityManager.persist(OtpChallengeEntity.from(challenge));
    entityManager.flush();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
  public <T> Optional<T> withLockedChallenge(OtpId id, Function<OTP, T> mutation) {
    return repository
        .findLockedById(id.value())
        .map(
            entity -> {
              OTP current = entity.toDomain();
              T result = mutation.apply(current);
              entity.copyFrom(current);
              repository.flush();
              return result;
            });
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OTP> findById(OtpId challengeId) {
    return repository.findById(challengeId.value()).map(OtpChallengeEntity::toDomain);
  }
}
