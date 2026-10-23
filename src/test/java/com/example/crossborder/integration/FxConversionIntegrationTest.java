package com.example.crossborder.integration;

import com.example.crossborder.fx.api.FxQuote;
import com.example.crossborder.fx.api.FxQuoteService;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.ConvertTransferRequest;
import com.example.crossborder.transfer.api.TransferResult;
import com.example.crossborder.transfer.application.TransferService;
import com.example.crossborder.wallet.api.WalletView;
import com.example.crossborder.wallet.application.WalletService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.main.web-application-type=none")
class FxConversionIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("TEST_DATABASE_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
            return;
        }
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgresContainer() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Autowired
    private FxQuoteService fxQuoteService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void conversionConsumesQuoteAndBooksFeesInLedger() {
        UUID customerId = UUID.randomUUID();
        WalletView sourceWallet = walletService.createWallet(customerId, "NGN");
        WalletView destinationWallet = walletService.createWallet(UUID.randomUUID(), "XAF");
        UUID sourceAccount = ledgerAccountId(sourceWallet.id());
        UUID destinationAccount = ledgerAccountId(destinationWallet.id());
        UUID fundingAccount = ledgerAccountService.createAccount("TEST_FUNDING", "NGN");
        ledgerPostingService.postTransaction(UUID.randomUUID(), "fund fx test", List.of(
                new LedgerEntryRequest(fundingAccount, new BigDecimal("20000.00"), BigDecimal.ZERO),
                new LedgerEntryRequest(sourceAccount, BigDecimal.ZERO, new BigDecimal("20000.00"))));

        FxQuote quote = fxQuoteService.createQuote(customerId, "NGN", "XAF", new BigDecimal("10000.00"));
        UUID ngnFeeAccount = fxQuoteService.getSystemAccount("NGN", "SYSTEM_FEE");
        UUID xafFeeAccount = fxQuoteService.getSystemAccount("XAF", "SYSTEM_FEE");
        BigDecimal ngnFeeBefore = ledgerBalanceService.getBalance(ngnFeeAccount);
        BigDecimal xafFeeBefore = ledgerBalanceService.getBalance(xafFeeAccount);
        TransferResult result = transferService.convert(customerId, "fx-convert-" + UUID.randomUUID(),
                new ConvertTransferRequest(sourceWallet.id(), destinationWallet.id(), quote.id()));

        assertEquals(201, result.status().value());
        assertEquals("COMPLETED", result.body().state());
        assertEquals(new BigDecimal("4096"), result.body().destinationAmount());
        assertEquals("XAF", result.body().destinationCurrency());
        assertEquals(quote.id(), result.body().quoteId());
        assertEquals(new BigDecimal("10000.00"), ledgerBalanceService.getBalance(sourceAccount));
        assertEquals(new BigDecimal("4096"), ledgerBalanceService.getBalance(destinationAccount));
        assertEquals(new BigDecimal("100.00"), ledgerBalanceService.getBalance(ngnFeeAccount).subtract(ngnFeeBefore));
        assertEquals(new BigDecimal("62"), ledgerBalanceService.getBalance(xafFeeAccount).subtract(xafFeeBefore));

        assertThrows(ApiException.class, () -> fxQuoteService.consumeQuote(customerId, quote.id()));
    }

    @Test
    void expiredQuoteCannotBeConsumed() {
        UUID customerId = UUID.randomUUID();
        FxQuote quote = fxQuoteService.createQuote(customerId, "NGN", "XAF", new BigDecimal("1000.00"));
        jdbcTemplate.update("UPDATE fx.quotes SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), quote.id());

        assertThrows(ApiException.class, () -> fxQuoteService.consumeQuote(customerId, quote.id()));
    }

    private UUID ledgerAccountId(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT ledger_account_id FROM wallet.wallets WHERE id = ?",
                UUID.class, walletId);
    }
}