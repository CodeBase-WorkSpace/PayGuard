/* Persistent scope rows serialize issuance even before a challenge exists. */
CREATE TABLE identity_challenge_scope (
    user_id varchar(255) COLLATE "C" NOT NULL,
    purpose varchar(32) NOT NULL,
    PRIMARY KEY (user_id, purpose)
);
INSERT INTO identity_challenge_scope(user_id, purpose)
SELECT DISTINCT user_id, purpose FROM identity_challenge;

/* Duplicate active rows are a migration error; this migration never discards data. */
CREATE UNIQUE INDEX ux_identity_one_active_challenge
    ON identity_challenge(user_id, purpose) WHERE status = 'ACTIVE';
