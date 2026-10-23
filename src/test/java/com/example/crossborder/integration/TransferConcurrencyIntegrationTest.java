package com.example.crossborder.integration;

import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.transfer.api.CreateTransferRequest;
import com.example.crossborder.transfer.api.TransferResult;
import com.example.crossborder.transfer.application.TransferService;
import com.example.crossborder.wallet.api.WalletView;
import com.example.crossborder.wallet.application.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.web-application-type=none")
class TransferConcurrencyIntegrationTest {

    private static final int PARALLEL_WITHDRAWALS = 50;
    private static final int FUNDED_WITHDRAWALS = 25;

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
    void fiftyParallelWithdrawalsCannotOverdrawWallet() throws Exception {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        WalletView source = walletService.createWallet(senderId, "NGN");
        WalletView destination = walletService.createWallet(receiverId, "NGN");
        UUID sourceAccount = ledgerAccountFor(source.id());
        UUID destinationAccount = ledgerAccountFor(destination.id());
        UUID fundingAccount = ledgerAccountService.createAccount("TEST_FUNDING", "NGN");

        ledgerPostingService.postTransaction(UUID.randomUUID(), "concurrency test funding", List.of(
            new LedgerEntryRequest(fundingAccount, new BigDecimal("25.00"), BigDecimal.ZERO),
            new LedgerEntryRequest(sourceAccount, BigDecimal.ZERO, new BigDecimal("25.00"))));

        ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_WITHDRAWALS);
        CountDownLatch ready = new CountDownLatch(PARALLEL_WITHDRAWALS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TransferResult>> results = new ArrayList<>();
            for (int index = 0; index < PARALLEL_WITHDRAWALS; index++) {
                String idempotencyKey = "parallel-withdrawal-" + index;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return transferService.transfer(senderId, idempotencyKey,
                            new CreateTransferRequest(source.id(), destination.id(), BigDecimal.ONE));
                }));
            }

            ready.await();
            start.countDown();

            int completed = 0;
            int rejected = 0;
            for (Future<TransferResult> result : results) {
                TransferResult transferResult = result.get();
                if (transferResult.status().is2xxSuccessful()) {
                    completed++;
                } else if (transferResult.status().value() == 422) {
                    rejected++;
                }
            }

            BigDecimal sourceLedgerBalance = ledgerBalanceService.getBalance(sourceAccount);
            BigDecimal destinationLedgerBalance = ledgerBalanceService.getBalance(destinationAccount);
            long sourceCachedMinor = cachedBalance(source.id());
            long destinationCachedMinor = cachedBalance(destination.id());

            assertEquals(FUNDED_WITHDRAWALS, completed);
            assertEquals(PARALLEL_WITHDRAWALS - FUNDED_WITHDRAWALS, rejected);
            assertTrue(sourceLedgerBalance.signum() >= 0);
            assertEquals(BigDecimal.ZERO.setScale(2), sourceLedgerBalance);
            assertEquals(new BigDecimal("25.00"), destinationLedgerBalance);
            assertEquals(0L, sourceCachedMinor);
            assertEquals(2500L, destinationCachedMinor);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID ledgerAccountFor(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT ledger_account_id FROM wallet.wallets WHERE id = ?",
                UUID.class, walletId);
    }

    private long cachedBalance(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT balance_minor FROM wallet.wallets WHERE id = ?",
                Long.class, walletId);
    }
}