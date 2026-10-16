-- Phase 6: indexes for merchant analytics aggregate queries (read-only reporting).
-- Event timestamps: captured_at, failed updated_at, succeeded refund completed_at.
-- Risk assessments already indexed by (merchant_id, evaluated_at) and decision.

UPDATE payflow_schema_meta
SET notes = 'Risk + analytics indexes for merchant overview/daily aggregates'
WHERE id = 1;

CREATE INDEX idx_payments_merchant_captured_at
    ON payments (merchant_id, captured_at)
    WHERE captured_at IS NOT NULL;

CREATE INDEX idx_payments_merchant_failed_updated
    ON payments (merchant_id, updated_at)
    WHERE status = 'FAILED';

CREATE INDEX idx_refunds_merchant_succeeded_completed
    ON refunds (merchant_id, completed_at)
    WHERE status = 'SUCCEEDED';
