-- Preserve V1/V2 history and rows while adopting the recovered jOOQ table names.
-- The old text status checks/index must be recreated against the enum type.
CREATE TYPE payguard.identity_status AS ENUM ('ACTIVE', 'USED', 'LOCKED', 'EXPIRED', 'INVALIDATED');
ALTER TABLE payguard.identity_challenge
    DROP CONSTRAINT ck_identity_status,
    DROP CONSTRAINT ck_identity_active_attempts,
    DROP CONSTRAINT ck_identity_consumption;
DROP INDEX payguard.ux_identity_one_active_challenge;
ALTER TABLE payguard.identity_challenge RENAME TO identity_otp;
ALTER TABLE payguard.identity_otp
    ALTER COLUMN status TYPE payguard.identity_status USING status::text::payguard.identity_status,
    ADD CONSTRAINT ck_identity_active_attempts CHECK (status <> 'ACTIVE' OR attempts < max_attempts),
    ADD CONSTRAINT ck_identity_consumption CHECK ((status = 'USED') = (consumed_at IS NOT NULL));
ALTER TABLE payguard.identity_challenge_scope RENAME TO identity_otp_scope;
ALTER TABLE payguard.identity_audit_log RENAME COLUMN challenge_id TO otp_id;
CREATE UNIQUE INDEX ux_identity_otp_active ON payguard.identity_otp(user_id, purpose) WHERE status = 'ACTIVE';
ALTER INDEX payguard.ix_identity_challenge_active RENAME TO ix_identity_otp_subject_status;
ALTER INDEX payguard.ix_identity_challenge_retention RENAME TO ix_identity_otp_retention;
ALTER INDEX payguard.ix_identity_audit_challenge RENAME TO ix_identity_audit_otp;
