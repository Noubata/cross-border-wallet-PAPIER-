package com.example.crossborder.funding.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.funding.api.CreateFundingRequest;
import com.example.crossborder.funding.api.FundingPaymentView;
import com.example.crossborder.funding.domain.FundingPayment;
import com.example.crossborder.funding.infrastructure.FakePaymentGateway;
import com.example.crossborder.funding.infrastructure.FundingRepository;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.api.WalletLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundingServiceTest {

    private FakePaymentGateway gateway;
    private FundingRepository fundingRepository;
    private WalletLockService walletLockService;
    private LedgerAccountService ledgerAccountService;
    private LedgerPostingService ledgerPostingService;
    private AuditWriter auditWriter;
    private FundingService service;

    @BeforeEach
    void setUp() {
        gateway = new FakePaymentGateway("test-webhook-secret");
        fundingRepository = mock(FundingRepository.class);
        walletLockService = mock(WalletLockService.class);
        ledgerAccountService = mock(LedgerAccountService.class);
        ledgerPostingService = mock(LedgerPostingService.class);
        auditWriter = mock(AuditWriter.class);
        service = new FundingService(gateway, fundingRepository, walletLockService,
                ledgerAccountService, ledgerPostingService, auditWriter, new ObjectMapper());
    }

    @Test
    void createPaymentInitializesGatewayAndPersistsIntent() {
        UUID customerId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(walletLockService.lockWallets(List.of(walletId))).thenReturn(List.of(
                new LockedWallet(walletId, customerId, "NGN", accountId, "ACTIVE")));
        FundingPaymentView view = service.createPayment(customerId,
                new CreateFundingRequest(walletId, new BigDecimal("50.00"), "NGN", "user@example.com"));

        assertEquals("PENDING", view.status());
        assertEquals(new BigDecimal("50.00"), view.amount());
        verify(fundingRepository).insertPayment(any(FundingPayment.class));
        verify(auditWriter).append(eq(customerId), eq("FUNDING_PAYMENT_CREATED"),
                eq("FUNDING_PAYMENT"), eq(view.id()), any());
    }

    @Test
    void webhookCreditsWalletExactlyOnce() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID walletAccount = UUID.randomUUID();
        UUID clearingAccount = UUID.randomUUID();
        String reference = "gateway-ref";
        FundingPayment payment = new FundingPayment(UUID.randomUUID(), customerId, walletId, walletAccount,
                "FAKE", reference, 1234, "NGN", "PENDING", null, Instant.now(), null);
        when(fundingRepository.findPaymentForUpdate(reference)).thenReturn(Optional.of(payment));
        when(walletLockService.lockWallets(List.of(walletId))).thenReturn(List.of(
                new LockedWallet(walletId, customerId, "NGN", walletAccount, "ACTIVE")));
        when(fundingRepository.findClearingAccount("NGN")).thenReturn(Optional.of(clearingAccount));
        UUID ledgerTransactionId = UUID.randomUUID();
        when(ledgerPostingService.postTransaction(any(UUID.class), any(), any())).thenReturn(ledgerTransactionId);
        when(fundingRepository.markSucceeded(reference, ledgerTransactionId)).thenReturn(true);
        byte[] body = webhookBody(reference, 1234, "NGN");
        String signature = gateway.signWebhook(body);

        var result = service.handleWebhook(body, signature);

        assertEquals(true, result.processed());
        assertEquals(false, result.duplicate());
        verify(ledgerPostingService).postTransaction(eq(payment.id()), any(),
                org.mockito.ArgumentMatchers.argThat(entries -> entries.size() == 2
                        && entries.get(0).debitAmount().compareTo(new BigDecimal("12.34")) == 0
                        && entries.get(1).creditAmount().compareTo(new BigDecimal("12.34")) == 0));
        verify(fundingRepository).markSucceeded(reference, ledgerTransactionId);
    }

    @Test
    void duplicateWebhookReturnsIdempotentResultWithoutPostingAgain() throws Exception {
        UUID customerId = UUID.randomUUID();
        FundingPayment succeeded = new FundingPayment(UUID.randomUUID(), customerId, UUID.randomUUID(),
                UUID.randomUUID(), "FAKE", "already-paid", 100, "XAF", "SUCCEEDED", null,
                Instant.now(), Instant.now());
        when(fundingRepository.findPaymentForUpdate("already-paid")).thenReturn(Optional.of(succeeded));
        byte[] body = webhookBody("already-paid", 100, "XAF");

        var result = service.handleWebhook(body, gateway.signWebhook(body));

        assertEquals(true, result.duplicate());
        verify(ledgerPostingService, never()).postTransaction(any(), any(), any());
    }

    @Test
    void rejectsInvalidSignatureAndMismatchedAmount() throws Exception {
        byte[] badSignatureBody = webhookBody("unknown", 100, "NGN");
        ApiException signatureException = assertThrows(ApiException.class,
                () -> service.handleWebhook(badSignatureBody, "bad-signature"));
        assertEquals(HttpStatus.UNAUTHORIZED, signatureException.status());

        UUID customerId = UUID.randomUUID();
        FundingPayment payment = new FundingPayment(UUID.randomUUID(), customerId, UUID.randomUUID(),
                UUID.randomUUID(), "FAKE", "amount-mismatch", 100, "NGN", "PENDING", null,
                Instant.now(), null);
        when(fundingRepository.findPaymentForUpdate("amount-mismatch")).thenReturn(Optional.of(payment));
        byte[] mismatchBody = webhookBody("amount-mismatch", 101, "NGN");
        ApiException amountException = assertThrows(ApiException.class,
                () -> service.handleWebhook(mismatchBody, gateway.signWebhook(mismatchBody)));
        assertEquals(HttpStatus.CONFLICT, amountException.status());
    }

    private byte[] webhookBody(String reference, long amount, String currency) {
        return ("{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + reference
                + "\",\"amount\":" + amount + ",\"currency\":\"" + currency + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

}