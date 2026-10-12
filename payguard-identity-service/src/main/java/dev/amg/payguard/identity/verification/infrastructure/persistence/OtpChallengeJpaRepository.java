package dev.amg.payguard.identity.verification.infrastructure.persistence;

import dev.amg.payguard.identity.verification.domain.enums.OtpPurpose;
import dev.amg.payguard.identity.verification.domain.enums.OtpStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OtpChallengeJpaRepository extends JpaRepository<OtpChallengeEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<OtpChallengeEntity> findByUserIdAndPurposeAndStatus(
      String userId, OtpPurpose purpose, OtpStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from OtpChallengeEntity c where c.id = :id")
  Optional<OtpChallengeEntity> findLockedById(@Param("id") String id);
}
