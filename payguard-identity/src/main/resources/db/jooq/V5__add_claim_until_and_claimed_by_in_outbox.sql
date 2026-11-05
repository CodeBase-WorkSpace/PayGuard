ALTER TABLE identity_outbox
    ADD COLUMN claimed_by uuid,
    ADD COLUMN claim_until timestamptz(6);