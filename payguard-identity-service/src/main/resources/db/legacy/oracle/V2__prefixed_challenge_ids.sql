-- UUID-to-prefixed-ID mapping cannot be inferred. Fail explicitly for populated
-- installations instead of deleting rows or inventing customer/challenge IDs.
DECLARE
    challenge_count NUMBER;
    audit_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO challenge_count FROM identity_challenge;
    SELECT COUNT(*) INTO audit_count FROM identity_audit_log;
    IF challenge_count > 0 OR audit_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20001,
            'Prefixed OTP IDs require an approved mapping for existing challenge/audit rows');
    END IF;
END;
/

ALTER TABLE identity_audit_log DROP CONSTRAINT fk_identity_audit_challenge;
-- Oracle represents the time-zone component of this composite index as a
-- function-based expression, preventing modification of another indexed column.
DROP INDEX ix_identity_audit_challenge;
ALTER TABLE identity_challenge MODIFY (id VARCHAR2(26 CHAR));
ALTER TABLE identity_audit_log MODIFY (challenge_id VARCHAR2(26 CHAR));
ALTER TABLE identity_audit_log ADD CONSTRAINT fk_identity_audit_challenge
    FOREIGN KEY (challenge_id) REFERENCES identity_challenge (id);
CREATE INDEX ix_identity_audit_challenge
    ON identity_audit_log (challenge_id, occurred_at);
