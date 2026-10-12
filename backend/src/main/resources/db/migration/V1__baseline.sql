-- PayFlow Phase 1 baseline.
-- Domain tables (merchants, payments, etc.) are intentionally deferred to later phases.
-- Flyway owns schema evolution; Hibernate ddl-auto remains validate.

CREATE TABLE payflow_schema_meta (
    id              SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    schema_phase    TEXT        NOT NULL,
    initialized_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    notes           TEXT        NOT NULL DEFAULT ''
);

INSERT INTO payflow_schema_meta (id, schema_phase, notes)
VALUES (1, 'phase-1', 'Baseline marker only; no payment domain schema yet.');
