-- Phase 4: double-entry ledger foundation (posting services follow later).
--
-- Accounting conventions (application-enforced when posting):
--   Capture: DEBIT PROCESSOR_CLEARING, CREDIT MERCHANT_PAYABLE (payment currency INR).
--   Successful refund: DEBIT MERCHANT_PAYABLE, CREDIT PROCESSOR_CLEARING.
--   Amounts are positive BIGINT minor units only — never floating point.
--
-- Balance invariant (every posted ledger_transaction):
--   SUM(amount_minor WHERE direction = DEBIT)
--     = SUM(amount_minor WHERE direction = CREDIT)
--   for that transaction (same currency on the parent row).
-- Ordinary row-level CHECK constraints cannot enforce this multi-row aggregate.
-- Future posting logic must insert the transaction + balanced entry pair in one DB
-- transaction and verify debits == credits before commit. This migration does NOT
-- claim the database already guarantees balanced postings.
--
-- Entries are append-only accounting records (no public update/delete API).

UPDATE payflow_schema_meta
SET notes = 'Refunds + ledger schema foundation; balanced postings enforced in app TX'
WHERE id = 1;

CREATE TABLE ledger_accounts (
    id            UUID PRIMARY KEY,
    merchant_id   UUID REFERENCES merchants (id),
    account_type  VARCHAR(32)  NOT NULL,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'INR',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ledger_accounts_type CHECK (
        account_type IN ('PROCESSOR_CLEARING', 'MERCHANT_PAYABLE')
    ),
    CONSTRAINT ck_ledger_accounts_currency CHECK (currency = 'INR'),
    CONSTRAINT ck_ledger_accounts_merchant_scope CHECK (
        (account_type = 'PROCESSOR_CLEARING' AND merchant_id IS NULL)
        OR (account_type = 'MERCHANT_PAYABLE' AND merchant_id IS NOT NULL)
    )
);

-- One platform clearing account per currency; one payable account per merchant+currency.
CREATE UNIQUE INDEX uq_ledger_accounts_platform_clearing
    ON ledger_accounts (account_type, currency)
    WHERE merchant_id IS NULL;

CREATE UNIQUE INDEX uq_ledger_accounts_merchant_payable
    ON ledger_accounts (merchant_id, account_type, currency)
    WHERE merchant_id IS NOT NULL;

CREATE INDEX idx_ledger_accounts_merchant ON ledger_accounts (merchant_id);

CREATE TABLE ledger_transactions (
    id            UUID PRIMARY KEY,
    source_type   VARCHAR(32)  NOT NULL,
    source_id     UUID         NOT NULL,
    merchant_id   UUID         NOT NULL REFERENCES merchants (id),
    currency      VARCHAR(3)   NOT NULL DEFAULT 'INR',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ledger_transactions_source UNIQUE (source_type, source_id),
    CONSTRAINT ck_ledger_transactions_source_type CHECK (
        source_type IN ('PAYMENT_CAPTURE', 'REFUND')
    ),
    CONSTRAINT ck_ledger_transactions_currency CHECK (currency = 'INR')
);

CREATE INDEX idx_ledger_transactions_merchant ON ledger_transactions (merchant_id);
CREATE INDEX idx_ledger_transactions_merchant_created
    ON ledger_transactions (merchant_id, created_at DESC);

CREATE TABLE ledger_entries (
    id                     UUID PRIMARY KEY,
    ledger_transaction_id  UUID         NOT NULL REFERENCES ledger_transactions (id),
    ledger_account_id      UUID         NOT NULL REFERENCES ledger_accounts (id),
    direction              VARCHAR(16)  NOT NULL,
    amount_minor           BIGINT       NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount_minor > 0)
);

CREATE INDEX idx_ledger_entries_transaction ON ledger_entries (ledger_transaction_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries (ledger_account_id);
