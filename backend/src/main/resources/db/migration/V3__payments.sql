-- Phase 3 foundation: payments table (state machine + API logic follow in later tasks).

UPDATE payflow_schema_meta
SET schema_phase = 'phase-3',
    notes = 'Payments schema foundation; simulated capture only (no settlement)'
WHERE id = 1;

CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID           NOT NULL REFERENCES merchants (id),
    merchant_reference  VARCHAR(64)    NOT NULL,
    amount_minor        BIGINT         NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status              VARCHAR(32)    NOT NULL,
    description         VARCHAR(500),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_merchant_reference UNIQUE (merchant_id, merchant_reference),
    CONSTRAINT ck_payments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_payments_status CHECK (
        status IN ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'CANCELLED')
    )
);

CREATE INDEX idx_payments_merchant ON payments (merchant_id);
CREATE INDEX idx_payments_merchant_status ON payments (merchant_id, status);
CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);
