CREATE TABLE java_idempotency_record (
    namespace VARCHAR(128) NOT NULL,
    key_digest CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    owner_token UUID,
    lease_expires_at TIMESTAMPTZ,
    record_expires_at TIMESTAMPTZ NOT NULL,
    result_codec VARCHAR(128),
    result_payload BYTEA,
    retryable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT java_idempotency_record_pk PRIMARY KEY (namespace, key_digest),
    CONSTRAINT java_idempotency_record_status_ck
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT java_idempotency_record_shape_ck CHECK (
        (status = 'PROCESSING'
            AND owner_token IS NOT NULL
            AND lease_expires_at IS NOT NULL
            AND result_codec IS NULL
            AND result_payload IS NULL
            AND retryable = FALSE)
        OR
        (status = 'COMPLETED'
            AND owner_token IS NULL
            AND lease_expires_at IS NULL
            AND result_codec IS NOT NULL
            AND result_payload IS NOT NULL
            AND retryable = FALSE)
        OR
        (status = 'FAILED'
            AND owner_token IS NULL
            AND lease_expires_at IS NULL
            AND result_codec IS NULL
            AND result_payload IS NULL)
    )
);

CREATE INDEX java_idempotency_record_expiry_idx
    ON java_idempotency_record (record_expires_at);
