-- Phase 5: durable webhook delivery jobs (at-least-once HTTP delivery with claim leases).
--
-- Enqueue path: Kafka consumer inserts PENDING rows in the SAME TX as processed_events.
-- Delivery worker claims with FOR UPDATE SKIP LOCKED, commits, then performs HTTPS outside TX.
-- Unique (event_id, endpoint_id) prevents duplicate jobs under at-least-once Kafka redelivery.

UPDATE payflow_schema_meta
SET notes = 'Webhook delivery jobs with claim leases; signed HTTPS delivery follows'
WHERE id = 1;

CREATE TABLE webhook_deliveries (
    id                 UUID PRIMARY KEY,
    event_id           UUID           NOT NULL,
    endpoint_id        UUID           NOT NULL REFERENCES webhook_endpoints (id) ON DELETE CASCADE,
    merchant_id        UUID           NOT NULL REFERENCES merchants (id),
    event_type         VARCHAR(128)   NOT NULL,
    payload            JSONB          NOT NULL,
    status             VARCHAR(32)    NOT NULL,
    attempt_count      INTEGER        NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ    NOT NULL,
    claim_token        UUID,
    claim_expires_at   TIMESTAMPTZ,
    last_http_status   INTEGER,
    last_error         VARCHAR(512),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    delivered_at       TIMESTAMPTZ,
    CONSTRAINT uq_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_webhook_deliveries_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_webhook_deliveries_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'DELIVERED', 'FAILED')
    ),
    CONSTRAINT ck_webhook_deliveries_status_shape CHECK (
        (status = 'PENDING'
            AND delivered_at IS NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
        OR (status = 'PROCESSING'
            AND delivered_at IS NULL
            AND claim_expires_at IS NOT NULL
            AND claim_token IS NOT NULL)
        OR (status = 'DELIVERED'
            AND delivered_at IS NOT NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
        OR (status = 'FAILED'
            AND delivered_at IS NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
    )
);

CREATE INDEX idx_webhook_deliveries_pending_poll
    ON webhook_deliveries (next_attempt_at ASC, id ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_webhook_deliveries_stale_claims
    ON webhook_deliveries (claim_expires_at ASC, id ASC)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_webhook_deliveries_failed
    ON webhook_deliveries (created_at DESC, id DESC)
    WHERE status = 'FAILED';

CREATE INDEX idx_webhook_deliveries_endpoint
    ON webhook_deliveries (endpoint_id, created_at DESC);

CREATE INDEX idx_webhook_deliveries_merchant
    ON webhook_deliveries (merchant_id, created_at DESC);
