ALTER TABLE wallet.wallets
    ADD CONSTRAINT wallets_balance_nonnegative CHECK (balance_minor >= 0);

DROP TRIGGER ledger_transaction_balanced ON ledger.entries;

CREATE OR REPLACE FUNCTION ledger.ensure_transaction_balanced() RETURNS trigger
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
     WHERE e.transaction_id = NEW.id;

    IF entry_count < 2 OR debit_total <> credit_total OR COALESCE(currency_mismatch, FALSE) THEN
        RAISE EXCEPTION 'Ledger transaction % is not balanced', NEW.id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_transaction_balanced
    AFTER INSERT ON ledger.transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.ensure_transaction_balanced();

