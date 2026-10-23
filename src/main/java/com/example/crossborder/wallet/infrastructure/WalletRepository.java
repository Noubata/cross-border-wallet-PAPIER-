package com.example.crossborder.wallet.infrastructure;

import com.example.crossborder.wallet.domain.Wallet;
import com.example.crossborder.wallet.domain.WalletCurrency;
import com.example.crossborder.wallet.api.LockedWallet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WalletRepository {

    private final JdbcTemplate jdbcTemplate;

    public WalletRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(UUID walletId, UUID customerId, WalletCurrency currency, UUID ledgerAccountId) {
        return jdbcTemplate.update("""
                INSERT INTO wallet.wallets
                    (id, customer_id, currency, ledger_account_id, balance_minor, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                ON CONFLICT (customer_id, currency) DO NOTHING
                """, walletId, customerId, currency.name(), ledgerAccountId) == 1;
    }

    public Optional<Wallet> findByCustomerAndCurrency(UUID customerId, WalletCurrency currency) {
        return jdbcTemplate.query("""
                        SELECT id, customer_id, currency, ledger_account_id, balance_minor, status, created_at
                          FROM wallet.wallets WHERE customer_id = ? AND currency = ?
                        """,
                resultSet -> resultSet.next() ? Optional.of(mapWallet(resultSet)) : Optional.empty(),
                customerId, currency.name());
    }

    public List<Wallet> findAllByCustomer(UUID customerId) {
        return jdbcTemplate.query("""
                        SELECT id, customer_id, currency, ledger_account_id, balance_minor, status, created_at
                          FROM wallet.wallets WHERE customer_id = ? ORDER BY currency
                        """,
                (resultSet, rowNumber) -> mapWallet(resultSet), customerId);
    }

    public int applyLedgerDelta(UUID ledgerAccountId, long balanceDelta) {
        return jdbcTemplate.update("""
                UPDATE wallet.wallets
                   SET balance_minor = balance_minor + ?
                 WHERE ledger_account_id = ?
                """, balanceDelta, ledgerAccountId);
    }

    public List<LockedWallet> lockWallets(List<UUID> walletIds) {
        if (walletIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(walletIds.size(), "?"));
        return jdbcTemplate.query("""
                        SELECT id, customer_id, currency, ledger_account_id, status
                          FROM wallet.wallets
                         WHERE id IN (%s)
                         ORDER BY id
                         FOR UPDATE
                        """.formatted(placeholders),
                (resultSet, rowNumber) -> new LockedWallet(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("customer_id", UUID.class),
                        resultSet.getString("currency").trim(),
                        resultSet.getObject("ledger_account_id", UUID.class),
                        resultSet.getString("status")), walletIds.toArray());
    }

    private Wallet mapWallet(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Wallet(resultSet.getObject("id", UUID.class),
                resultSet.getObject("customer_id", UUID.class),
                WalletCurrency.fromCode(resultSet.getString("currency").trim()),
                resultSet.getObject("ledger_account_id", UUID.class),
                resultSet.getLong("balance_minor"), resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toInstant());
    }
}