-- Phase 4: durable refund create idempotency (Postgres SoT; no Redis locks).
-- Scoped independently from payment create keys via operation = REFUND_CREATE.
--
-- Retention / expiry policy (matches payment create):
--   * expires_at = created_at + 72 hours when a record is written.
--   * Future cleanup may delete only COMPLETED rows with expires_at < NOW().
--   * Never delete active STARTED leases; this migration does not install a deleter.
--
-- Lock order for refund create (avoids deadlocks with capacity checks):
--   1) lock parent payment row FOR UPDATE
--   2) reserve / lock refund_idempotency_records
--   3) insert/update refunds

UPDATE payflow_schema_meta
SET notes = 'Refunds schema + refund-create idempotency records (72h retention policy)'
WHERE id = 1;

CREATE TABLE refund_idempotency_records (
    id                   UUID PRIMARY KEY,
    merchant_id          UUID          NOT NULL REFERENCES merchants (id),
    operation            VARCHAR(64)   NOT NULL,
    idempotency_key      VARCHAR(128)  NOT NULL,
    request_fingerprint  VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    refund_id            UUID          REFERENCES refunds (id),
    http_status          INTEGER,
    response_json        TEXT,
    locked_at            TIMESTAMPTZ   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refund_idempotency_scope
        UNIQUE (merchant_id, operation, idempotency_key),
    CONSTRAINT ck_refund_idempotency_status
        CHECK (status IN ('STARTED', 'COMPLETED')),
    CONSTRAINT ck_refund_idempotency_completed_shape CHECK (
        (status = 'STARTED'
            AND refund_id IS NULL
            AND http_status IS NULL
            AND response_json IS NULL)
        OR
        (status = 'COMPLETED'
            AND refund_id IS NOT NULL
            AND http_status IS NOT NULL
            AND response_json IS NOT NULL)
    )
);

CREATE INDEX idx_refund_idempotency_merchant
    ON refund_idempotency_records (merchant_id);

CREATE INDEX idx_refund_idempotency_expires
    ON refund_idempotency_records (expires_at)
    WHERE status = 'COMPLETED';
