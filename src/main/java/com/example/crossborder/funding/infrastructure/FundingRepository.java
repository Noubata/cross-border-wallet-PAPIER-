package com.example.crossborder.funding.infrastructure;

import com.example.crossborder.funding.domain.FundingPayment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FundingRepository {

    private final JdbcTemplate jdbcTemplate;

    public FundingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertPayment(FundingPayment payment) {
        jdbcTemplate.update("""
                INSERT INTO funding.payments
                    (id, customer_id, wallet_id, ledger_account_id, gateway, gateway_reference,
                     amount_minor, currency, status, authorization_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, payment.id(), payment.customerId(), payment.walletId(), payment.ledgerAccountId(),
                payment.gateway(), payment.reference(), payment.amountMinor(), payment.currency(),
                payment.authorizationUrl());
    }

    public Optional<FundingPayment> findPaymentForUpdate(String reference) {
        return jdbcTemplate.query("""
                        SELECT id, customer_id, wallet_id, ledger_account_id, gateway, gateway_reference,
                               amount_minor, currency, status, authorization_url, created_at, completed_at
                          FROM funding.payments WHERE gateway_reference = ? FOR UPDATE
                        """,
                resultSet -> resultSet.next() ? Optional.of(mapPayment(resultSet)) : Optional.empty(), reference);
    }

    public boolean markSucceeded(String reference, UUID ledgerTransactionId) {
        return jdbcTemplate.update("""
                UPDATE funding.payments
                   SET status = 'SUCCEEDED', ledger_transaction_id = ?, completed_at = now()
                 WHERE gateway_reference = ? AND status = 'PENDING'
                """, ledgerTransactionId, reference) == 1;
    }

    public boolean markFailed(String reference) {
        return jdbcTemplate.update("""
                UPDATE funding.payments SET status = 'FAILED', completed_at = now()
                 WHERE gateway_reference = ? AND status = 'PENDING'
                """, reference) == 1;
    }

    public Optional<UUID> findClearingAccount(String currency) {
        return jdbcTemplate.query("SELECT ledger_account_id FROM funding.clearing_accounts WHERE currency = ?",
                resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getObject("ledger_account_id", UUID.class)) : Optional.empty(),
                currency);
    }

    public void insertClearingAccount(String currency, UUID accountId) {
        jdbcTemplate.update("""
                INSERT INTO funding.clearing_accounts (currency, ledger_account_id)
                VALUES (?, ?) ON CONFLICT (currency) DO NOTHING
                """, currency, accountId);
    }

    private FundingPayment mapPayment(ResultSet resultSet) throws SQLException {
        Timestamp completedAt = resultSet.getTimestamp("completed_at");
        return new FundingPayment(resultSet.getObject("id", UUID.class),
                resultSet.getObject("customer_id", UUID.class), resultSet.getObject("wallet_id", UUID.class),
                resultSet.getObject("ledger_account_id", UUID.class), resultSet.getString("gateway"),
                resultSet.getString("gateway_reference"), resultSet.getLong("amount_minor"),
                resultSet.getString("currency").trim(), resultSet.getString("status"),
                resultSet.getString("authorization_url"), resultSet.getTimestamp("created_at").toInstant(),
                completedAt == null ? null : completedAt.toInstant());
    }
}