package com.example.crossborder.transfer.infrastructure;

import com.example.crossborder.transfer.domain.IdempotencyRecord;
import com.example.crossborder.transfer.domain.TransferRecord;
import com.example.crossborder.transfer.domain.TransferState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claimIdempotency(UUID customerId, String operation, String key, String requestHash) {
        return jdbcTemplate.update("""
                INSERT INTO transfer.idempotency_requests
                    (id, customer_id, operation, idempotency_key, request_hash)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (customer_id, operation, idempotency_key) DO NOTHING
                """, UUID.randomUUID(), customerId, operation, key, requestHash) == 1;
    }

    public Optional<IdempotencyRecord> findIdempotency(UUID customerId, String operation, String key) {
        return jdbcTemplate.query("""
                        SELECT id, request_hash, transfer_id, response_body
                          FROM transfer.idempotency_requests
                         WHERE customer_id = ? AND operation = ? AND idempotency_key = ?
                         FOR UPDATE
                        """,
                resultSet -> resultSet.next() ? Optional.of(new IdempotencyRecord(
                        resultSet.getObject("id", UUID.class), resultSet.getString("request_hash"),
                        resultSet.getObject("transfer_id", UUID.class), resultSet.getString("response_body")))
                        : Optional.empty(), customerId, operation, key);
    }

    public void insertTransfer(UUID transferId, UUID customerId, UUID sourceWalletId,
                               UUID destinationWalletId, long amountMinor, String currency) {
        jdbcTemplate.update("""
                INSERT INTO transfer.transfers
                    (id, customer_id, source_wallet_id, destination_wallet_id,
                     source_amount_minor, destination_amount_minor, source_currency,
                     destination_currency, fee_minor, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'PENDING')
                """, transferId, customerId, sourceWalletId, destinationWalletId,
                amountMinor, amountMinor, currency, currency);
    }

    public boolean transitionPending(UUID transferId, TransferState target, String failureCode) {
        return jdbcTemplate.update("""
                UPDATE transfer.transfers
                   SET state = ?, failure_code = ?,
                       completed_at = CASE WHEN ? = 'COMPLETED' THEN now() ELSE completed_at END
                 WHERE id = ? AND state = 'PENDING'
                """, target.name(), failureCode, target.name(), transferId) == 1;
    }

    public Optional<TransferRecord> findTransfer(UUID transferId) {
        return jdbcTemplate.query("""
                        SELECT id, customer_id, source_wallet_id, destination_wallet_id,
                               source_amount_minor, source_currency, fee_minor, state,
                               created_at, completed_at
                          FROM transfer.transfers WHERE id = ?
                        """,
                resultSet -> resultSet.next() ? Optional.of(mapTransfer(resultSet)) : Optional.empty(), transferId);
    }

    public void completeIdempotency(UUID customerId, String operation, String key,
                                    UUID transferId, String responseBody) {
        jdbcTemplate.update("""
                UPDATE transfer.idempotency_requests
                   SET transfer_id = ?, response_status = 201, response_body = CAST(? AS jsonb)
                 WHERE customer_id = ? AND operation = ? AND idempotency_key = ?
                """, transferId, responseBody, customerId, operation, key);
    }

    private TransferRecord mapTransfer(ResultSet resultSet) throws SQLException {
        long amountMinor = resultSet.getLong("source_amount_minor");
        String currency = resultSet.getString("source_currency").trim();
        int scale = currency.equals("NGN") ? 2 : 0;
        java.sql.Timestamp completed = resultSet.getTimestamp("completed_at");
        return new TransferRecord(resultSet.getObject("id", UUID.class),
                resultSet.getObject("customer_id", UUID.class),
                resultSet.getObject("source_wallet_id", UUID.class),
                resultSet.getObject("destination_wallet_id", UUID.class),
                BigDecimal.valueOf(amountMinor, scale), currency,
                BigDecimal.valueOf(resultSet.getLong("fee_minor"), scale),
                TransferState.valueOf(resultSet.getString("state")),
                resultSet.getTimestamp("created_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}