-- Phase 5 foundation: transactional outbox (Postgres SoT for unpublished domain events).
--
-- Write path (future): INSERT outbox_events in the SAME DB transaction as the related
-- payment/refund/ledger state change. Publication is at-least-once via a later poller;
-- consumers must deduplicate by event id. No Kafka producer ships in this migration.
--
-- Claim / crash recovery (future publisher):
--   * Claim a ready row → status PROCESSING, set claim_expires_at = now() + lease.
--   * On success → PUBLISHED + published_at.
--   * On retryable failure → PENDING, bump attempt_count, set next_attempt_at (backoff),
--     clear claim_expires_at, store sanitized last_error (≤512 chars).
--   * Stale PROCESSING rows (claim_expires_at < now()) are reclaimable like PENDING.
--   * After bounded attempts (application policy, not a CHECK), mark FAILED for inspection.
--     Ops may later reset FAILED → PENDING (next_attempt_at = now()) for manual retry.
--
-- Payload policy: JSON business facts only — never passwords, tokens, API secrets, or
-- unnecessary PII.

UPDATE payflow_schema_meta
SET schema_phase = 'phase-5',
    notes = 'Transactional outbox foundation; Kafka publisher/webhooks follow later'
WHERE id = 1;

CREATE TABLE outbox_events (
    id                   UUID PRIMARY KEY,
    aggregate_type       VARCHAR(64)    NOT NULL,
    aggregate_id         UUID           NOT NULL,
    event_type           VARCHAR(128)   NOT NULL,
    event_schema_version INTEGER        NOT NULL,
    merchant_id          UUID           REFERENCES merchants (id),
    payload              JSONB          NOT NULL,
    status               VARCHAR(32)    NOT NULL,
    attempt_count        INTEGER        NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    published_at         TIMESTAMPTZ,
    last_error           VARCHAR(512),
    claim_expires_at     TIMESTAMPTZ,
    CONSTRAINT ck_outbox_events_schema_version CHECK (event_schema_version >= 1),
    CONSTRAINT ck_outbox_events_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_outbox_events_status_shape CHECK (
        (status = 'PENDING'
            AND published_at IS NULL
            AND claim_expires_at IS NULL)
        OR (status = 'PROCESSING'
            AND published_at IS NULL
            AND claim_expires_at IS NOT NULL)
        OR (status = 'PUBLISHED'
            AND published_at IS NOT NULL
            AND claim_expires_at IS NULL)
        OR (status = 'FAILED'
            AND published_at IS NULL
            AND claim_expires_at IS NULL)
    )
);

-- Ready-to-publish poll (status PENDING, due by next_attempt_at).
CREATE INDEX idx_outbox_events_pending_poll
    ON outbox_events (next_attempt_at ASC, id ASC)
    WHERE status = 'PENDING';

-- Crash recovery: reclaim PROCESSING rows whose lease expired.
CREATE INDEX idx_outbox_events_stale_claims
    ON outbox_events (claim_expires_at ASC, id ASC)
    WHERE status = 'PROCESSING';

-- Inspect / manually requeue exhausted failures.
CREATE INDEX idx_outbox_events_failed
    ON outbox_events (created_at DESC, id DESC)
    WHERE status = 'FAILED';

-- Operator / aggregate diagnostics.
CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, created_at DESC);

CREATE INDEX idx_outbox_events_merchant_created
    ON outbox_events (merchant_id, created_at DESC)
    WHERE merchant_id IS NOT NULL;
