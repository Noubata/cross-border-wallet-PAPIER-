CREATE SCHEMA fx;

CREATE TABLE fx.rates (
    id UUID PRIMARY KEY,
    base_currency CHAR(3) NOT NULL CHECK (base_currency = 'NGN'),
    quote_currency CHAR(3) NOT NULL CHECK (quote_currency = 'XAF'),
    mid_rate NUMERIC(24, 12) NOT NULL CHECK (mid_rate > 0),
    spread_basis_points INTEGER NOT NULL CHECK (spread_basis_points >= 0 AND spread_basis_points < 10000),
    provider VARCHAR(40) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX fx_rates_observed_at_idx ON fx.rates(observed_at DESC);

CREATE TABLE fx.quotes (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    source_currency CHAR(3) NOT NULL CHECK (source_currency IN ('NGN', 'XAF')),
    destination_currency CHAR(3) NOT NULL CHECK (destination_currency IN ('NGN', 'XAF')),
    source_amount_minor BIGINT NOT NULL CHECK (source_amount_minor > 0),
    destination_amount_minor BIGINT NOT NULL CHECK (destination_amount_minor > 0),
    gross_destination_minor BIGINT NOT NULL CHECK (gross_destination_minor >= destination_amount_minor),
    flat_fee_source_minor BIGINT NOT NULL CHECK (flat_fee_source_minor >= 0),
    spread_fee_destination_minor BIGINT NOT NULL CHECK (spread_fee_destination_minor >= 0),
    mid_rate NUMERIC(24, 12) NOT NULL CHECK (mid_rate > 0),
    effective_rate NUMERIC(24, 12) NOT NULL CHECK (effective_rate > 0),
    rate_id UUID NOT NULL REFERENCES fx.rates(id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (source_currency <> destination_currency)
);

CREATE INDEX fx_quotes_customer_created_idx ON fx.quotes(customer_id, created_at DESC);

CREATE TABLE fx.system_accounts (
    currency CHAR(3) NOT NULL CHECK (currency IN ('NGN', 'XAF')),
    account_type VARCHAR(24) NOT NULL CHECK (account_type IN ('FX_CLEARING', 'SYSTEM_FEE')),
    ledger_account_id UUID NOT NULL UNIQUE REFERENCES ledger.accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (currency, account_type)
);

ALTER TABLE transfer.transfers
    ADD CONSTRAINT transfers_fx_quote_fk FOREIGN KEY (quote_id) REFERENCES fx.quotes(id);