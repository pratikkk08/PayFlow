-- Phase 6 foundation: configurable simulated-payment risk rules and immutable assessments.
--
-- Schema/persistence ONLY in this migration. Risk score calculation, velocity evaluation,
-- rule execution against payments, risk APIs, and payment-authorization integration are
-- NOT implemented yet — they follow in later Phase 6 tasks.
--
-- Audit design:
--   risk_assessments and risk_rule_matches are append-oriented evaluation records.
--   Matches denormalize rule_type, matched_action, reason_code, and score_contribution
--   so later edits to risk_rules (parameters/action/priority/enabled) do not rewrite history.
--   configuration_version on assessments is an opaque snapshot identifier for auditability.
--   Composite FK (payment_id, merchant_id) → payments(id, merchant_id) keeps merchant scope consistent.

UPDATE payflow_schema_meta
SET schema_phase = 'phase-6',
    notes = 'Risk rules + assessment schema foundation; scoring/evaluation not implemented yet'
WHERE id = 1;

-- Supports composite FK from risk_assessments (payment_id, merchant_id).
ALTER TABLE payments
    ADD CONSTRAINT uq_payments_id_merchant UNIQUE (id, merchant_id);

CREATE TABLE risk_rules (
    id              UUID PRIMARY KEY,
    merchant_id     UUID           NOT NULL REFERENCES merchants (id),
    rule_type       VARCHAR(64)    NOT NULL,
    parameters      JSONB          NOT NULL,
    action          VARCHAR(16)    NOT NULL,
    priority        INTEGER        NOT NULL,
    enabled         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_risk_rules_type CHECK (
        rule_type IN ('AMOUNT_THRESHOLD', 'PAYMENT_VELOCITY', 'REPEATED_FAILURES')
    ),
    CONSTRAINT ck_risk_rules_action CHECK (
        action IN ('FLAG', 'REVIEW', 'BLOCK')
    ),
    CONSTRAINT ck_risk_rules_priority CHECK (priority >= 0),
    CONSTRAINT ck_risk_rules_parameters_object CHECK (jsonb_typeof(parameters) = 'object')
);

CREATE INDEX idx_risk_rules_merchant_priority
    ON risk_rules (merchant_id, priority ASC, id ASC);

CREATE INDEX idx_risk_rules_merchant_enabled
    ON risk_rules (merchant_id, priority ASC, id ASC)
    WHERE enabled = TRUE;

CREATE INDEX idx_risk_rules_merchant_type
    ON risk_rules (merchant_id, rule_type);

CREATE TABLE risk_assessments (
    id                      UUID PRIMARY KEY,
    payment_id              UUID           NOT NULL,
    merchant_id             UUID           NOT NULL,
    decision                VARCHAR(16)    NOT NULL,
    risk_score              INTEGER        NOT NULL,
    evaluated_at            TIMESTAMPTZ    NOT NULL,
    reason_codes            JSONB,
    configuration_version   VARCHAR(64)    NOT NULL,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_risk_assessments_payment UNIQUE (payment_id),
    CONSTRAINT fk_risk_assessments_payment_merchant
        FOREIGN KEY (payment_id, merchant_id) REFERENCES payments (id, merchant_id),
    CONSTRAINT fk_risk_assessments_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants (id),
    CONSTRAINT ck_risk_assessments_decision CHECK (
        decision IN ('ALLOW', 'FLAG', 'REVIEW', 'BLOCK')
    ),
    CONSTRAINT ck_risk_assessments_score CHECK (
        risk_score >= 0 AND risk_score <= 100
    ),
    CONSTRAINT ck_risk_assessments_reason_codes_array CHECK (
        reason_codes IS NULL OR jsonb_typeof(reason_codes) = 'array'
    ),
    CONSTRAINT ck_risk_assessments_configuration_version CHECK (
        length(trim(configuration_version)) > 0
    )
);

CREATE INDEX idx_risk_assessments_merchant_evaluated
    ON risk_assessments (merchant_id, evaluated_at DESC, id DESC);

CREATE INDEX idx_risk_assessments_decision
    ON risk_assessments (merchant_id, decision, evaluated_at DESC);

CREATE TABLE risk_rule_matches (
    id                   UUID PRIMARY KEY,
    assessment_id        UUID           NOT NULL REFERENCES risk_assessments (id) ON DELETE CASCADE,
    rule_id              UUID           NOT NULL REFERENCES risk_rules (id),
    rule_type            VARCHAR(64)    NOT NULL,
    matched_action       VARCHAR(16)    NOT NULL,
    reason_code          VARCHAR(64),
    score_contribution   INTEGER        NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_risk_rule_matches_assessment_rule UNIQUE (assessment_id, rule_id),
    CONSTRAINT ck_risk_rule_matches_rule_type CHECK (
        rule_type IN ('AMOUNT_THRESHOLD', 'PAYMENT_VELOCITY', 'REPEATED_FAILURES')
    ),
    CONSTRAINT ck_risk_rule_matches_action CHECK (
        matched_action IN ('FLAG', 'REVIEW', 'BLOCK')
    ),
    CONSTRAINT ck_risk_rule_matches_score_contribution CHECK (
        score_contribution >= 0 AND score_contribution <= 100
    ),
    CONSTRAINT ck_risk_rule_matches_reason_code CHECK (
        reason_code IS NULL OR length(trim(reason_code)) > 0
    )
);

CREATE INDEX idx_risk_rule_matches_assessment
    ON risk_rule_matches (assessment_id);

CREATE INDEX idx_risk_rule_matches_rule
    ON risk_rule_matches (rule_id);
