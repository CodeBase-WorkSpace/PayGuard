-- Serializes issuance by customer and purpose,
-- including when no OTP challenge exists yet.

CREATE TABLE identity_otp_scope
(
    user_id varchar(255) COLLATE "C" NOT NULL,
    purpose varchar(32)              NOT NULL,

    PRIMARY KEY (user_id, purpose),

    CONSTRAINT ck_identity_scope_customer_id
        CHECK (user_id ~ '^cus_[A-Za-z0-9]{22}$')
);