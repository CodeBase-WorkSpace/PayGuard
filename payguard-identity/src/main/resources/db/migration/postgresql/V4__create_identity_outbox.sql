CREATE TABLE identity_outbox
(
    event_id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_id    varchar(26) COLLATE "C" NOT NULL,
    event_type      varchar(128) NOT NULL,
    payload         jsonb NOT NULL,
    occurred_at     timestamptz(6) NOT NULL,
    created_at      timestamptz(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at    timestamptz(6),
    next_attempt_at timestamptz(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    attempt_count   integer DEFAULT 0 NOT NULL,

    CONSTRAINT ck_identity_outbox_payload
        CHECK (jsonb_typeof(payload) = 'object'),

    CONSTRAINT ck_identity_outbox_attempts
        CHECK (attempt_count >= 0)
);

CREATE INDEX ix_identity_outbox_pending
    ON identity_outbox (next_attempt_at, created_at, event_id)
    WHERE published_at IS NULL;