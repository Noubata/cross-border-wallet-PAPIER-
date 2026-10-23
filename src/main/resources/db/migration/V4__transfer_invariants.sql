ALTER TABLE transfer.transfers
    ADD CONSTRAINT transfers_distinct_wallets CHECK (source_wallet_id <> destination_wallet_id),
    ADD CONSTRAINT transfers_source_amount_positive CHECK (source_amount_minor > 0),
    ADD CONSTRAINT transfers_destination_amount_positive CHECK (destination_amount_minor > 0),
    ADD CONSTRAINT transfers_fee_nonnegative CHECK (fee_minor >= 0);

ALTER TABLE transfer.idempotency_requests
    ADD CONSTRAINT idempotency_key_nonblank CHECK (length(btrim(idempotency_key)) > 0),
    ADD CONSTRAINT idempotency_request_hash_hex CHECK (request_hash ~ '^[0-9a-f]{64}$');