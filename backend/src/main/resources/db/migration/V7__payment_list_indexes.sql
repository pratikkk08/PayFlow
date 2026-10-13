-- Phase 3: indexes aligned to payment list sort (created_at DESC, id DESC)
-- and status-filtered merchant listings.

UPDATE payflow_schema_meta
SET notes = 'Payments APIs including list/filter indexes (created_at, id)'
WHERE id = 1;

DROP INDEX IF EXISTS idx_payments_merchant_created;

CREATE INDEX idx_payments_merchant_created_id
    ON payments (merchant_id, created_at DESC, id DESC);

CREATE INDEX idx_payments_merchant_status_created_id
    ON payments (merchant_id, status, created_at DESC, id DESC);
