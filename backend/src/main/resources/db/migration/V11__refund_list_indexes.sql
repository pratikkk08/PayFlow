-- Phase 4: refund history pagination (created_at DESC, id DESC) per payment.

UPDATE payflow_schema_meta
SET notes = 'Refunds, ledger, and refund history list index'
WHERE id = 1;

CREATE INDEX idx_refunds_payment_created_id
    ON refunds (payment_id, created_at DESC, id DESC);
