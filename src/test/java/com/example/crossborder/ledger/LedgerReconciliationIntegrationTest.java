package com.example.crossborder.integration;

import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.wallet.application.WalletService;
import com.example.crossborder.wallet.api.WalletView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.main.web-application-type=none")
class LedgerReconciliationIntegrationTest {

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
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

        @Autowired
        private PlatformTransactionManager transactionManager;

    @Test
    void cachedWalletBalanceReconcilesWithLedgerEntries() {
        UUID customerId = UUID.randomUUID();
        WalletView wallet = walletService.createWallet(customerId, "NGN");
        UUID walletAccountId = jdbcTemplate.queryForObject(
                "SELECT ledger_account_id FROM wallet.wallets WHERE id = ?", UUID.class, wallet.id());
        UUID fundingAccountId = ledgerAccountService.createAccount("TEST_FUNDING", "NGN");

        ledgerPostingService.postTransaction(UUID.randomUUID(), "test funding", List.of(
                new LedgerEntryRequest(fundingAccountId, new BigDecimal("250.00"), BigDecimal.ZERO),
                new LedgerEntryRequest(walletAccountId, BigDecimal.ZERO, new BigDecimal("250.00"))));

        long cachedMinor = jdbcTemplate.queryForObject(
                "SELECT balance_minor FROM wallet.wallets WHERE id = ?", Long.class, wallet.id());
        BigDecimal ledgerBalance = ledgerBalanceService.getBalance(walletAccountId);
        BigDecimal cachedBalance = new BigDecimal(cachedMinor).movePointLeft(2);

        assertEquals(new BigDecimal("250.00"), ledgerBalance);
        assertEquals(ledgerBalance, cachedBalance);
    }

    @Test
    void databaseRejectsUnbalancedLedgerTransactionAtCommit() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID transactionId = UUID.randomUUID();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> transactionTemplate.execute(status -> {
            jdbcTemplate.update("""
                    INSERT INTO ledger.transactions (id, operation_id, currency, description)
                    VALUES (?, ?, 'XAF', 'empty transaction')
                    """, transactionId, UUID.randomUUID());
            return null;
        }));
    }
}