package com.example.crossborder.integration;

import com.example.crossborder.admin.application.AdminService;
import com.example.crossborder.audit.api.AuditEventView;
import com.example.crossborder.customer.api.CustomerRegistrationService;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.transfer.api.CreateTransferRequest;
import com.example.crossborder.transfer.api.TransferResponse;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.main.web-application-type=none")
class RiskAdminIntegrationTest {

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
    private CustomerRegistrationService customerRegistrationService;

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
    private AdminService adminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flaggedTransferWaitsForAdminApprovalAndWalletActionsAreAudited() {
        UUID customerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        customerRegistrationService.createPendingProfile(customerId, "en");
        jdbcTemplate.update("UPDATE customer.profiles SET kyc_status = 'VERIFIED' WHERE customer_id = ?", customerId);

        WalletView sourceWallet = walletService.createWallet(customerId, "NGN");
        WalletView destinationWallet = walletService.createWallet(recipientId, "NGN");
        UUID sourceAccount = ledgerAccountId(sourceWallet.id());
        UUID destinationAccount = ledgerAccountId(destinationWallet.id());
        UUID fundingAccount = ledgerAccountService.createAccount("TEST_FUNDING", "NGN");
        ledgerPostingService.postTransaction(UUID.randomUUID(), "risk admin funding", List.of(
                new LedgerEntryRequest(fundingAccount, new BigDecimal("600000.00"), BigDecimal.ZERO),
                new LedgerEntryRequest(sourceAccount, BigDecimal.ZERO, new BigDecimal("600000.00"))));

        var created = transferService.transfer(customerId, "risk-flag-" + UUID.randomUUID(),
                new CreateTransferRequest(sourceWallet.id(), destinationWallet.id(), new BigDecimal("600000.00")));

        assertEquals(202, created.status().value());
        assertEquals("FLAGGED", created.body().state());
        assertEquals(new BigDecimal("600000.00"), ledgerBalanceService.getBalance(sourceAccount));
        assertEquals(0, ledgerBalanceService.getBalance(destinationAccount).compareTo(BigDecimal.ZERO));
        assertTrue(adminService.getFlaggedTransfers().stream()
            .anyMatch(transfer -> transfer.id().equals(created.body().id())));

        TransferResponse approved = adminService.approveTransfer(adminId, created.body().id());
        assertEquals("COMPLETED", approved.state());
        assertEquals(0, ledgerBalanceService.getBalance(sourceAccount).compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("600000.00"), ledgerBalanceService.getBalance(destinationAccount));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin.transfer_reviews WHERE transfer_id = ? AND decision = 'APPROVED'",
                Integer.class, created.body().id()));

        adminService.freezeWallet(adminId, destinationWallet.id());
        assertEquals("FROZEN", walletService.getWallet(recipientId, "NGN").status());
        adminService.unfreezeWallet(adminId, destinationWallet.id());
        assertEquals("ACTIVE", walletService.getWallet(recipientId, "NGN").status());

        List<AuditEventView> events = adminService.getAuditEvents(100);
        assertTrue(events.stream().anyMatch(event -> event.action().equals("TRANSFER_APPROVED")));
        assertTrue(events.stream().anyMatch(event -> event.action().equals("WALLET_FROZEN")));
        assertTrue(events.stream().anyMatch(event -> event.action().equals("WALLET_UNFROZEN")));
    }

    private UUID ledgerAccountId(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT ledger_account_id FROM wallet.wallets WHERE id = ?",
                UUID.class, walletId);
    }
}