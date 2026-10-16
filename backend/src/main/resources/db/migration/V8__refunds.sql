-- Phase 4 foundation: refunds against captured payments (API/service logic follows later).
--
-- Invariant (enforced in application transactions, not solely by CHECK constraints):
--   sum(amount_minor) for refunds in (PENDING, SUCCEEDED) for a payment
--   must never exceed that payment's amount_minor.
-- PENDING rows reserve refundable capacity until they become SUCCEEDED or FAILED.
-- FAILED refunds release reserved capacity. Payments stay CAPTURED when partially/fully
-- refunded — there is no payment-level REFUNDED status.

UPDATE payflow_schema_meta
SET schema_phase = 'phase-4',
    notes = 'Refunds schema foundation; capacity invariant enforced in app TX (PENDING reserves)'
WHERE id = 1;

CREATE TABLE refunds (
    id                   UUID PRIMARY KEY,
    payment_id           UUID           NOT NULL REFERENCES payments (id),
    merchant_id          UUID           NOT NULL REFERENCES merchants (id),
    merchant_reference   VARCHAR(64)    NOT NULL,
    amount_minor         BIGINT         NOT NULL,
    currency             VARCHAR(3)     NOT NULL DEFAULT 'INR',
    status               VARCHAR(32)    NOT NULL,
    failure_code         VARCHAR(64),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    completed_at         TIMESTAMPTZ,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_refunds_merchant_reference UNIQUE (merchant_id, merchant_reference),
    CONSTRAINT ck_refunds_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_refunds_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_refunds_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_refunds_completed_at CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_refunds_payment ON refunds (payment_id);
CREATE INDEX idx_refunds_merchant ON refunds (merchant_id);
CREATE INDEX idx_refunds_payment_status ON refunds (payment_id, status);
CREATE INDEX idx_refunds_merchant_created ON refunds (merchant_id, created_at DESC);
