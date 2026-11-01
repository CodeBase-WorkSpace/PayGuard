CREATE TYPE identity_status AS ENUM (
    'ACTIVE',
    'USED',
    'LOCKED',
    'EXPIRED',
    'INVALIDATED'
    );

CREATE TABLE identity_otp
(
    id                  varchar(26) COLLATE "C" PRIMARY KEY,
    user_id             varchar(255) COLLATE "C" NOT NULL,
    purpose             varchar(32) NOT NULL,
    channel             varchar(32) NOT NULL,
    verification_mode   varchar(32) NOT NULL,
    code_hash           varchar(512),
    device_binding_hash varchar(512),
    attempts            integer DEFAULT 0 NOT NULL,
    max_attempts        integer NOT NULL,
    status              identity_status NOT NULL,
    created_at          timestamptz(6) NOT NULL,
    expires_at          timestamptz(6) NOT NULL,
    consumed_at         timestamptz(6),
    retention_until     timestamptz(6) NOT NULL,

    CONSTRAINT ck_identity_otp_id
        CHECK (id ~ '^otp_[A-Za-z0-9]{22}$'),

    CONSTRAINT ck_identity_customer_id
        CHECK (user_id ~ '^cus_[A-Za-z0-9]{22}$'),

    CONSTRAINT ck_identity_attempts
        CHECK (max_attempts > 0
            AND attempts BETWEEN 0 AND max_attempts),

    CONSTRAINT ck_identity_active_attempts
        CHECK (status <> 'ACTIVE' OR attempts < max_attempts),

    CONSTRAINT ck_identity_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT ck_identity_consumption
        CHECK ((status = 'USED') = (consumed_at IS NOT NULL)),

    CONSTRAINT ck_identity_consumption_time
        CHECK (
            consumed_at IS NULL
                OR (consumed_at >= created_at AND consumed_at < expires_at)
            ),

    CONSTRAINT ck_identity_code_hash
        CHECK (
            verification_mode <> 'RANDOM_NUMERIC'
                OR (code_hash IS NOT NULL AND length(btrim(code_hash)) > 0)
            ),

    CONSTRAINT ck_identity_binding_hash
        CHECK (
            device_binding_hash IS NULL
                OR length(btrim(device_binding_hash)) > 0
            )

    -- [jooq ignore start]
    ,
    CONSTRAINT ck_identity_retention
        CHECK (
            retention_until >= (
                (
                    (expires_at AT TIME ZONE 'UTC')
                        + INTERVAL '3 years'
                    ) AT TIME ZONE 'UTC'
                )
            )
    -- [jooq ignore stop]
);

CREATE UNIQUE INDEX ux_identity_otp_active
    ON identity_otp (user_id, purpose)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_identity_otp_retention
    ON identity_otp (retention_until);
