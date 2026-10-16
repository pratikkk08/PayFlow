-- Phase 5: per-claim token so stale publishers cannot mark another worker's row PUBLISHED/FAILED.

UPDATE payflow_schema_meta
SET notes = 'Outbox claim tokens for concurrent publisher safety; Kafka publisher follows'
WHERE id = 1;

ALTER TABLE outbox_events
    ADD COLUMN claim_token UUID;

ALTER TABLE outbox_events
    DROP CONSTRAINT ck_outbox_events_status_shape;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status_shape CHECK (
        (status = 'PENDING'
            AND published_at IS NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
        OR (status = 'PROCESSING'
            AND published_at IS NULL
            AND claim_expires_at IS NOT NULL
            AND claim_token IS NOT NULL)
        OR (status = 'PUBLISHED'
            AND published_at IS NOT NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
        OR (status = 'FAILED'
            AND published_at IS NULL
            AND claim_expires_at IS NULL
            AND claim_token IS NULL)
    );

CREATE INDEX idx_outbox_events_claim_token
    ON outbox_events (claim_token)
    WHERE claim_token IS NOT NULL;
