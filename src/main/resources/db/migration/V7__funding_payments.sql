CREATE SCHEMA funding;

CREATE TABLE funding.payments (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    wallet_id UUID NOT NULL REFERENCES wallet.wallets(id),
    ledger_account_id UUID NOT NULL REFERENCES ledger.accounts(id),
    gateway VARCHAR(24) NOT NULL CHECK (gateway IN ('PAYSTACK_TEST', 'FAKE')),
    gateway_reference VARCHAR(128) NOT NULL UNIQUE,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL CHECK (currency IN ('NGN', 'XAF')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    authorization_url TEXT,
    ledger_transaction_id UUID REFERENCES ledger.transactions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX funding_payments_customer_created_idx ON funding.payments(customer_id, created_at DESC);

CREATE TABLE funding.clearing_accounts (
    currency CHAR(3) PRIMARY KEY CHECK (currency IN ('NGN', 'XAF')),
    ledger_account_id UUID NOT NULL UNIQUE REFERENCES ledger.accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);