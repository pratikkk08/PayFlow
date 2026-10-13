-- Phase 3: durable payment create idempotency (Postgres SoT; no Redis locks).
--
-- Retention / expiry policy:
--   * expires_at is set to created_at + 72 hours when a record is written.
--   * Future cleanup may delete only COMPLETED rows with expires_at < NOW().
--   * Never delete STARTED rows that are still within the processing lease
--     (locked_at within the last few minutes); they indicate an in-flight create.
--   * This migration does not install a deleter — expiry is recorded for policy only.

UPDATE payflow_schema_meta
SET schema_phase = 'phase-3',
    notes = 'Payments schema + create-payment idempotency records'
WHERE id = 1;

CREATE TABLE payment_idempotency_records (
    id                   UUID PRIMARY KEY,
    merchant_id          UUID          NOT NULL REFERENCES merchants (id),
    operation            VARCHAR(64)   NOT NULL,
    idempotency_key      VARCHAR(128)  NOT NULL,
    request_fingerprint  VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    payment_id           UUID          REFERENCES payments (id),
    http_status          INTEGER,
    response_json        TEXT,
    locked_at            TIMESTAMPTZ   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_idempotency_scope
        UNIQUE (merchant_id, operation, idempotency_key),
    CONSTRAINT ck_payment_idempotency_status
        CHECK (status IN ('STARTED', 'COMPLETED')),
    CONSTRAINT ck_payment_idempotency_completed_shape CHECK (
        (status = 'STARTED'
            AND payment_id IS NULL
            AND http_status IS NULL
            AND response_json IS NULL)
        OR
        (status = 'COMPLETED'
            AND payment_id IS NOT NULL
            AND http_status IS NOT NULL
            AND response_json IS NOT NULL)
    )
);

CREATE INDEX idx_payment_idempotency_merchant
    ON payment_idempotency_records (merchant_id);

CREATE INDEX idx_payment_idempotency_expires
    ON payment_idempotency_records (expires_at)
    WHERE status = 'COMPLETED';
