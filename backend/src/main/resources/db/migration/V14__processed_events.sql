-- Phase 5: durable consumer deduplication for at-least-once Kafka domain events.
--
-- Consumers insert a row in the SAME PostgreSQL transaction as successful handling.
-- Primary key (event_id, consumer_identity) makes redelivery a no-op per logical consumer
-- while allowing distinct consumer identities to process the same event independently.
-- Failed handling must roll back — never leave a marker without a committed side effect.

UPDATE payflow_schema_meta
SET notes = 'Idempotent Kafka consumers via processed_events; webhooks follow'
WHERE id = 1;

CREATE TABLE processed_events (
    event_id           UUID           NOT NULL,
    consumer_identity  VARCHAR(128)   NOT NULL,
    event_type         VARCHAR(128)   NOT NULL,
    schema_version     INTEGER        NOT NULL,
    aggregate_type     VARCHAR(64)    NOT NULL,
    aggregate_id       UUID           NOT NULL,
    processed_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_identity),
    CONSTRAINT ck_processed_events_schema_version CHECK (schema_version >= 1),
    CONSTRAINT ck_processed_events_consumer_identity CHECK (length(trim(consumer_identity)) > 0)
);

CREATE INDEX idx_processed_events_processed_at
    ON processed_events (processed_at DESC, event_id DESC);

CREATE INDEX idx_processed_events_aggregate
    ON processed_events (aggregate_type, aggregate_id, processed_at DESC);
