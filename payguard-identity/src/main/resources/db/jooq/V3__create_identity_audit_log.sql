CREATE TABLE identity_audit_log
(
    id          uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    otp_id      varchar(26) COLLATE "C" NOT NULL
        REFERENCES identity_otp (id),
    user_id     varchar(255) COLLATE "C" NOT NULL,
    action      varchar(32) NOT NULL,
    reason      varchar(128) NOT NULL,
    source_ip   varchar(64),
    occurred_at timestamptz(6) NOT NULL
);

CREATE INDEX ix_identity_audit_subject_time
    ON identity_audit_log (user_id, occurred_at);

CREATE INDEX ix_identity_audit_otp
    ON identity_audit_log (otp_id, occurred_at);