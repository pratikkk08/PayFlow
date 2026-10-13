-- Phase 3: simulated authorization outcome fields (no real card data).

UPDATE payflow_schema_meta
SET notes = 'Payments + idempotency + simulated authorization outcome fields'
WHERE id = 1;

ALTER TABLE payments
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN authorized_at TIMESTAMPTZ,
    ADD COLUMN processor_ref VARCHAR(64);

ALTER TABLE payments
    ADD CONSTRAINT ck_payments_failure_code_when_failed CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL)
    );
