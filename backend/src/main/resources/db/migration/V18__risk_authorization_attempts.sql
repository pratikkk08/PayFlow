-- Phase 6.4: durable merchant-scoped authorization attempts for concurrency-safe velocity.
--
-- Race (pre-6.4): payment-row locks alone do not serialize different payments of the same
-- merchant. Concurrent authorize TXes can each observe incomplete peer history when counting
-- only payment.created_at / FAILED updated_at under READ COMMITTED.
--
-- Strategy:
--   1) Authorize locks payment FOR UPDATE, then merchant FOR UPDATE (always this order).
--   2) Insert one risk_authorization_attempts row per payment (idempotent on payment_id).
--   3) PAYMENT_VELOCITY counts peer attempts by attempted_at in the evaluation window.
--   4) REPEATED_FAILURES still counts FAILED payments by updated_at; merchant lock makes
--      committed failures from prior authorizes visible before the next evaluation.
--
-- Limitations: only authorize is serialized per merchant (not create/capture/refund).
-- Holding the merchant lock for the full authorize TX can increase authorize latency under load.

UPDATE payflow_schema_meta
SET notes = 'Risk authorize attempts + merchant-scoped velocity serialization (Task 6.4)'
WHERE id = 1;

CREATE TABLE risk_authorization_attempts (
    id              UUID PRIMARY KEY,
    merchant_id     UUID           NOT NULL REFERENCES merchants (id),
    payment_id      UUID           NOT NULL,
    attempted_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_risk_authorization_attempts_payment UNIQUE (payment_id),
    CONSTRAINT fk_risk_authorization_attempts_payment_merchant
        FOREIGN KEY (payment_id, merchant_id) REFERENCES payments (id, merchant_id)
);

CREATE INDEX idx_risk_authorization_attempts_merchant_attempted
    ON risk_authorization_attempts (merchant_id, attempted_at DESC, payment_id);
