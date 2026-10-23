package com.example.crossborder.ledger.infrastructure;

import com.example.crossborder.ledger.domain.LedgerAccount;
import com.example.crossborder.ledger.domain.LedgerCurrency;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class LedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createAccount(String accountType, LedgerCurrency currency) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO ledger.accounts (id, account_type, currency)
                VALUES (?, ?, ?)
                """, id, accountType, currency.name());
        return id;
    }

    public Optional<LedgerAccount> findAccount(UUID accountId) {
        return jdbcTemplate.query("""
                        SELECT id, account_type, currency FROM ledger.accounts WHERE id = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new LedgerAccount(resultSet.getObject("id", UUID.class),
                        resultSet.getString("account_type"),
                        LedgerCurrency.fromCode(resultSet.getString("currency").trim())))
                        : Optional.empty(), accountId);
    }

    public void insertTransaction(UUID transactionId, UUID operationId, LedgerCurrency currency,
                                  String description) {
        jdbcTemplate.update("""
                INSERT INTO ledger.transactions (id, operation_id, currency, description)
                VALUES (?, ?, ?, ?)
                """, transactionId, operationId, currency.name(), description);
    }

    public void insertEntry(UUID entryId, UUID transactionId, UUID accountId,
                            long debitMinor, long creditMinor) {
        jdbcTemplate.update("""
                INSERT INTO ledger.entries (id, transaction_id, account_id, debit_minor, credit_minor)
                VALUES (?, ?, ?, ?, ?)
                """, entryId, transactionId, accountId, debitMinor, creditMinor);
    }

    public long getBalanceMinor(UUID accountId) {
        Long balance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(credit_minor - debit_minor), 0)
                  FROM ledger.entries WHERE account_id = ?
                """, Long.class, accountId);
        return balance == null ? 0 : balance;
    }
}