package dev.amg.payguard.otp.infrastructure.persistence;

import dev.amg.payguard.otp.domain.OtpChallengeStatus;
import dev.amg.payguard.otp.domain.OtpPurpose;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OtpChallengeJpaRepository extends JpaRepository<OtpChallengeEntity, UUID> {

  List<OtpChallengeEntity> findByUserIdAndPurposeAndStatus(
      String userId, OtpPurpose purpose, OtpChallengeStatus status);
}
