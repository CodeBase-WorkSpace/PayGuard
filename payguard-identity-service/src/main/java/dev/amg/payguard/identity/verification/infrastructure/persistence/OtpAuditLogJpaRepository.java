package dev.amg.payguard.identity.verification.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OtpAuditLogJpaRepository extends JpaRepository<OtpAuditLogEntity, UUID> {}
