-- Phase 5: merchant webhook endpoint registration (no delivery in this migration).
--
-- Signing secrets are stored only as AES-GCM ciphertext. Plaintext is returned once
-- at creation (application layer) and never selected by public APIs.
--
-- SSRF note: registration validates URL shape only. Delivery workers MUST re-resolve
-- DNS, reject blocked addresses, and refuse redirects — registration alone is not safe.

UPDATE payflow_schema_meta
SET notes = 'Webhook endpoint registration; delivery/signing workers follow'
WHERE id = 1;

CREATE TABLE webhook_endpoints (
    id                         UUID PRIMARY KEY,
    merchant_id                UUID           NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    url                        VARCHAR(2048)  NOT NULL,
    signing_secret_ciphertext  TEXT           NOT NULL,
    secret_prefix              VARCHAR(24)    NOT NULL,
    enabled                    BOOLEAN        NOT NULL DEFAULT TRUE,
    subscribed_event_types     JSONB          NOT NULL,
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    secret_rotated_at          TIMESTAMPTZ    NOT NULL,
    version                    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_webhook_endpoints_merchant_url UNIQUE (merchant_id, url),
    CONSTRAINT ck_webhook_endpoints_url_https CHECK (lower(url) LIKE 'https://%'),
    CONSTRAINT ck_webhook_endpoints_secret_prefix CHECK (length(trim(secret_prefix)) > 0),
    CONSTRAINT ck_webhook_endpoints_events_array CHECK (jsonb_typeof(subscribed_event_types) = 'array'),
    CONSTRAINT ck_webhook_endpoints_events_nonempty CHECK (jsonb_array_length(subscribed_event_types) >= 1)
);

CREATE INDEX idx_webhook_endpoints_merchant_created
    ON webhook_endpoints (merchant_id, created_at DESC, id DESC);

CREATE INDEX idx_webhook_endpoints_merchant_enabled
    ON webhook_endpoints (merchant_id, id)
    WHERE enabled = TRUE;

-- Delivery lookup: find enabled endpoints subscribed to a given event type.
CREATE INDEX idx_webhook_endpoints_subscribed_events
    ON webhook_endpoints USING GIN (subscribed_event_types);
