-- Phase 3: simulated capture/cancel timestamps (not real settlement).

UPDATE payflow_schema_meta
SET notes = 'Payments + idempotency + authorize + capture/cancel timestamps'
WHERE id = 1;

ALTER TABLE payments
    ADD COLUMN captured_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ;
