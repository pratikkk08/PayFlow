-- Phase 2: identity, refresh sessions, merchants, API keys, audit.

UPDATE payflow_schema_meta
SET schema_phase = 'phase-2',
    notes = 'Identity, merchants, and API keys schema'
WHERE id = 1;

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    full_name         VARCHAR(200)  NOT NULL,
    email             VARCHAR(320)  NOT NULL,
    email_normalized  VARCHAR(320)  NOT NULL,
    password_hash     VARCHAR(255)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email_normalized UNIQUE (email_normalized)
);

CREATE TABLE user_roles (
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role     VARCHAR(32) NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE refresh_token_families (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at     TIMESTAMPTZ,
    revoke_reason  VARCHAR(64)
);

CREATE INDEX idx_refresh_token_families_user ON refresh_token_families (user_id);

CREATE TABLE refresh_tokens (
    id                    UUID PRIMARY KEY,
    family_id             UUID         NOT NULL REFERENCES refresh_token_families (id) ON DELETE CASCADE,
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash            VARCHAR(64)  NOT NULL,
    expires_at            TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    consumed_at           TIMESTAMPTZ,
    replaced_by_token_id  UUID,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_unconsumed ON refresh_tokens (token_hash)
    WHERE consumed_at IS NULL;

CREATE TABLE merchants (
    id             UUID PRIMARY KEY,
    name           VARCHAR(200)  NOT NULL,
    description    VARCHAR(2000),
    status         VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    owner_user_id  UUID          NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_merchants_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'))
);

CREATE INDEX idx_merchants_owner ON merchants (owner_user_id);

CREATE TABLE merchant_memberships (
    id           UUID PRIMARY KEY,
    merchant_id  UUID         NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(32)  NOT NULL DEFAULT 'OWNER',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_merchant_memberships UNIQUE (merchant_id, user_id),
    CONSTRAINT ck_merchant_memberships_role CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE INDEX idx_merchant_memberships_user ON merchant_memberships (user_id);

CREATE TABLE api_keys (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID         NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    key_type            VARCHAR(32)  NOT NULL,
    mode                VARCHAR(16)  NOT NULL DEFAULT 'TEST',
    key_prefix          VARCHAR(40)  NOT NULL,
    secret_hash         VARCHAR(64),
    public_value        VARCHAR(128),
    created_by_user_id  UUID         NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at        TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    CONSTRAINT ck_api_keys_type CHECK (key_type IN ('SECRET', 'PUBLISHABLE')),
    CONSTRAINT ck_api_keys_mode CHECK (mode IN ('TEST', 'LIVE')),
    CONSTRAINT ck_api_keys_secret_fields CHECK (
        (key_type = 'SECRET' AND secret_hash IS NOT NULL AND public_value IS NULL)
        OR (key_type = 'PUBLISHABLE' AND public_value IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_api_keys_secret_hash_active
    ON api_keys (secret_hash)
    WHERE secret_hash IS NOT NULL AND revoked_at IS NULL;

CREATE INDEX idx_api_keys_merchant ON api_keys (merchant_id);
CREATE INDEX idx_api_keys_prefix ON api_keys (key_prefix);

CREATE TABLE security_audit_events (
    id             UUID PRIMARY KEY,
    event_type     VARCHAR(64)  NOT NULL,
    actor_user_id  UUID,
    merchant_id    UUID,
    api_key_id     UUID,
    detail         VARCHAR(500),
    ip_address     VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_security_audit_created ON security_audit_events (created_at DESC);
CREATE INDEX idx_security_audit_actor ON security_audit_events (actor_user_id);
