CREATE SCHEMA wallet;
CREATE SCHEMA ledger;
CREATE SCHEMA transfer;

CREATE TABLE ledger.accounts (
    id UUID PRIMARY KEY,
    account_type VARCHAR(24) NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency IN ('NGN', 'XAF')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wallet.wallets (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency IN ('NGN', 'XAF')),
    ledger_account_id UUID NOT NULL UNIQUE REFERENCES ledger.accounts(id),
    balance_minor BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_id, currency)
);

CREATE TABLE ledger.transactions (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency IN ('NGN', 'XAF')),
    description VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger.entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger.transactions(id),
    account_id UUID NOT NULL REFERENCES ledger.accounts(id),
    debit_minor BIGINT NOT NULL DEFAULT 0 CHECK (debit_minor >= 0),
    credit_minor BIGINT NOT NULL DEFAULT 0 CHECK (credit_minor >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (debit_minor > 0 AND credit_minor = 0) OR
        (credit_minor > 0 AND debit_minor = 0)
    )
);

CREATE INDEX ledger_entries_account_created_idx
    ON ledger.entries (account_id, created_at);

CREATE TABLE transfer.transfers (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    source_wallet_id UUID NOT NULL REFERENCES wallet.wallets(id),
    destination_wallet_id UUID NOT NULL REFERENCES wallet.wallets(id),
    source_amount_minor BIGINT NOT NULL CHECK (source_amount_minor > 0),
    destination_amount_minor BIGINT NOT NULL CHECK (destination_amount_minor > 0),
    source_currency CHAR(3) NOT NULL CHECK (source_currency IN ('NGN', 'XAF')),
    destination_currency CHAR(3) NOT NULL CHECK (destination_currency IN ('NGN', 'XAF')),
    fee_minor BIGINT NOT NULL DEFAULT 0 CHECK (fee_minor >= 0),
    state VARCHAR(16) NOT NULL
        CHECK (state IN ('PENDING', 'COMPLETED', 'FAILED', 'FLAGGED')),
    quote_id UUID,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE transfer.idempotency_requests (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    transfer_id UUID REFERENCES transfer.transfers(id),
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (customer_id, operation, idempotency_key)
);

CREATE FUNCTION ledger.reject_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are append-only';
END;
$$;

CREATE TRIGGER ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger.entries
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger.entries
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER ledger_transactions_immutable
    BEFORE UPDATE OR DELETE ON ledger.transactions
    FOR EACH ROW EXECUTE FUNCTION ledger.reject_mutation();

CREATE TRIGGER ledger_transactions_no_truncate
    BEFORE TRUNCATE ON ledger.transactions
    FOR EACH STATEMENT EXECUTE FUNCTION ledger.reject_mutation();

CREATE FUNCTION ledger.ensure_transaction_balanced() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    entry_count BIGINT;
    debit_total NUMERIC;
    credit_total NUMERIC;
    currency_mismatch BOOLEAN;
BEGIN
    SELECT COUNT(*), COALESCE(SUM(e.debit_minor), 0), COALESCE(SUM(e.credit_minor), 0),
           BOOL_OR(a.currency <> t.currency)
      INTO entry_count, debit_total, credit_total, currency_mismatch
      FROM ledger.entries e
      JOIN ledger.accounts a ON a.id = e.account_id
      JOIN ledger.transactions t ON t.id = e.transaction_id
     WHERE e.transaction_id = NEW.transaction_id;

    IF entry_count < 2 OR debit_total <> credit_total OR COALESCE(currency_mismatch, FALSE) THEN
        RAISE EXCEPTION 'Ledger transaction % is not balanced', NEW.transaction_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_transaction_balanced
    AFTER INSERT ON ledger.entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.ensure_transaction_balanced();

COMMENT ON COLUMN wallet.wallets.balance_minor IS
    'Cached ledger-derived balance: kobo for NGN and whole units for XAF.';