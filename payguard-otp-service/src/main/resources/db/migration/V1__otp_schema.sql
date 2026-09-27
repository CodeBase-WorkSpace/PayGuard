CREATE TABLE otp_challenge (
    id RAW(16) NOT NULL,
    user_id VARCHAR2(255 CHAR) NOT NULL,
    purpose VARCHAR2(32 CHAR) NOT NULL,
    channel VARCHAR2(32 CHAR) NOT NULL,
    verification_mode VARCHAR2(32 CHAR) NOT NULL,
    code_hash VARCHAR2(512 CHAR),
    device_binding_hash VARCHAR2(512 CHAR),
    attempts NUMBER(3) DEFAULT 0 NOT NULL,
    max_attempts NUMBER(3) NOT NULL,
    status VARCHAR2(20 CHAR) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    retention_until TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_otp_challenge PRIMARY KEY (id),
    CONSTRAINT ck_otp_challenge_attempts CHECK (attempts >= 0 AND attempts <= max_attempts)
);

CREATE INDEX ix_otp_challenge_active
    ON otp_challenge (user_id, purpose, status);
CREATE INDEX ix_otp_challenge_retention
    ON otp_challenge (retention_until);

CREATE TABLE otp_audit_log (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    challenge_id RAW(16) NOT NULL,
    user_id VARCHAR2(255 CHAR) NOT NULL,
    action VARCHAR2(32 CHAR) NOT NULL,
    reason VARCHAR2(128 CHAR) NOT NULL,
    source_ip VARCHAR2(64 CHAR),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_otp_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_otp_audit_challenge FOREIGN KEY (challenge_id)
        REFERENCES otp_challenge (id)
);

CREATE INDEX ix_otp_audit_subject_time
    ON otp_audit_log (user_id, occurred_at);
CREATE INDEX ix_otp_audit_challenge
    ON otp_audit_log (challenge_id, occurred_at);
