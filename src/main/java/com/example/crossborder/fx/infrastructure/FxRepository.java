package com.example.crossborder.fx.infrastructure;

import com.example.crossborder.fx.api.FxQuote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FxRepository {

    private final JdbcTemplate jdbcTemplate;

    public FxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID insertRate(BigDecimal midRate, int spreadBasisPoints, String provider) {
        UUID rateId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO fx.rates (id, base_currency, quote_currency, mid_rate,
                                      spread_basis_points, provider)
                VALUES (?, 'NGN', 'XAF', ?, ?, ?)
                """, rateId, midRate, spreadBasisPoints, provider);
        return rateId;
    }

    public void insertQuote(FxQuote quote, UUID customerId, long sourceAmountMinor,
                            long destinationAmountMinor, long grossDestinationMinor,
                            long flatFeeSourceMinor, long spreadFeeDestinationMinor, UUID rateId) {
        jdbcTemplate.update("""
                INSERT INTO fx.quotes
                    (id, customer_id, source_currency, destination_currency, source_amount_minor,
                     destination_amount_minor, gross_destination_minor, flat_fee_source_minor,
                     spread_fee_destination_minor, mid_rate, effective_rate, rate_id, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, quote.id(), customerId, quote.sourceCurrency(), quote.destinationCurrency(),
                sourceAmountMinor, destinationAmountMinor, grossDestinationMinor, flatFeeSourceMinor,
                spreadFeeDestinationMinor, quote.midRate(), quote.effectiveRate(), rateId,
                Timestamp.from(quote.expiresAt()));
    }

    public Optional<QuoteAmounts> lockQuote(UUID customerId, UUID quoteId) {
        return jdbcTemplate.query("""
                        SELECT id, source_currency, destination_currency, source_amount_minor,
                               destination_amount_minor, gross_destination_minor, flat_fee_source_minor,
                               spread_fee_destination_minor, mid_rate, effective_rate, expires_at, used_at
                          FROM fx.quotes WHERE id = ? AND customer_id = ? FOR UPDATE
                        """,
                resultSet -> resultSet.next() ? Optional.of(new QuoteAmounts(
                        resultSet.getObject("id", UUID.class), resultSet.getString("source_currency").trim(),
                        resultSet.getString("destination_currency").trim(), resultSet.getLong("source_amount_minor"),
                        resultSet.getLong("destination_amount_minor"), resultSet.getLong("gross_destination_minor"),
                        resultSet.getLong("flat_fee_source_minor"),
                        resultSet.getLong("spread_fee_destination_minor"), resultSet.getBigDecimal("mid_rate"),
                        resultSet.getBigDecimal("effective_rate"),
                        resultSet.getTimestamp("expires_at").toInstant(),
                        resultSet.getTimestamp("used_at") == null ? null
                                : resultSet.getTimestamp("used_at").toInstant())) : Optional.empty(),
                quoteId, customerId);
    }

    public boolean markQuoteUsed(UUID quoteId) {
        return jdbcTemplate.update("UPDATE fx.quotes SET used_at = now() WHERE id = ? AND used_at IS NULL",
                quoteId) == 1;
    }

    public Optional<UUID> findSystemAccount(String currency, String accountType) {
        return jdbcTemplate.query("""
                        SELECT ledger_account_id FROM fx.system_accounts
                         WHERE currency = ? AND account_type = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getObject("ledger_account_id", UUID.class))
                        : Optional.empty(), currency, accountType);
    }

    public void insertSystemAccount(String currency, String accountType, UUID ledgerAccountId) {
        jdbcTemplate.update("""
                INSERT INTO fx.system_accounts (currency, account_type, ledger_account_id)
                VALUES (?, ?, ?)
                ON CONFLICT (currency, account_type) DO NOTHING
                """, currency, accountType, ledgerAccountId);
    }

    public record QuoteAmounts(UUID id, String sourceCurrency, String destinationCurrency,
                               long sourceAmountMinor, long destinationAmountMinor,
                               long grossDestinationMinor, long flatFeeSourceMinor,
                               long spreadFeeDestinationMinor, BigDecimal midRate,
                               BigDecimal effectiveRate, Instant expiresAt, Instant usedAt) {
    }
}