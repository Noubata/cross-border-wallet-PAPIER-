package com.example.crossborder.transfer.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.fx.api.FxQuoteService;
import com.example.crossborder.fx.api.FxQuote;
import com.example.crossborder.risk.api.RiskDecision;
import com.example.crossborder.risk.api.RiskEvaluationService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.CreateTransferRequest;
import com.example.crossborder.transfer.api.ConvertTransferRequest;
import com.example.crossborder.transfer.api.TransferResponse;
import com.example.crossborder.transfer.domain.IdempotencyRecord;
import com.example.crossborder.transfer.domain.TransferRecord;
import com.example.crossborder.transfer.infrastructure.TransferRepository;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.api.WalletLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private TransferRepository transferRepository;
    private WalletLockService walletLockService;
    private LedgerBalanceService ledgerBalanceService;
    private LedgerPostingService ledgerPostingService;
    private AuditWriter auditWriter;
        private FxQuoteService fxQuoteService;
        private RiskEvaluationService riskEvaluationService;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferRepository = mock(TransferRepository.class);
        walletLockService = mock(WalletLockService.class);
        ledgerBalanceService = mock(LedgerBalanceService.class);
        ledgerPostingService = mock(LedgerPostingService.class);
        auditWriter = mock(AuditWriter.class);
        fxQuoteService = mock(FxQuoteService.class);
        riskEvaluationService = mock(RiskEvaluationService.class);
        when(riskEvaluationService.evaluate(any(UUID.class), any(UUID.class), any(BigDecimal.class), anyString()))
                .thenReturn(new RiskDecision(RiskDecision.Outcome.APPROVE, List.of()));
        transferService = new TransferService(transferRepository, walletLockService, ledgerBalanceService,
                ledgerPostingService, auditWriter, fxQuoteService, riskEvaluationService,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void transferPostsBalancedLedgerEntriesAndStoresOriginalResponse() {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(sourceId, destinationId, new BigDecimal("10.25"));
        when(transferRepository.claimIdempotency(eq(customerId), eq("WALLET_TRANSFER"), eq("key"), anyString()))
                .thenReturn(true);
        when(walletLockService.lockWallets(List.of(sourceId, destinationId))).thenReturn(List.of(
                new LockedWallet(sourceId, customerId, "NGN", sourceAccountId, "ACTIVE"),
                new LockedWallet(destinationId, UUID.randomUUID(), "NGN", destinationAccountId, "ACTIVE")));
        when(ledgerBalanceService.getBalance(sourceAccountId)).thenReturn(new BigDecimal("20.00"));
        doAnswer(invocation -> {
                    transferIdHolder[0] = invocation.getArgument(0);
                    return null;
                }).when(transferRepository).insertTransfer(any(UUID.class), eq(customerId), eq(sourceId),
                eq(destinationId), eq(1025L), eq("NGN"));
        when(transferRepository.findTransfer(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                new TransferRecord(invocation.getArgument(0), customerId, sourceId, destinationId,
                        new BigDecimal("10.25"), "NGN", BigDecimal.ZERO,
                        com.example.crossborder.transfer.domain.TransferState.COMPLETED,
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"))));
        when(transferRepository.transitionPending(any(UUID.class),
                eq(com.example.crossborder.transfer.domain.TransferState.COMPLETED), eq(null))).thenReturn(true);

        var result = transferService.transfer(customerId, "key", request);

        assertAll(
                () -> assertEquals(HttpStatus.CREATED, result.status()),
                () -> assertEquals("COMPLETED", result.body().state()),
                () -> assertEquals(transferIdHolder[0], result.body().id()));
        verify(ledgerPostingService).postTransaction(eq(transferIdHolder[0]), anyString(), any());
        verify(transferRepository).completeIdempotency(eq(customerId), eq("WALLET_TRANSFER"), eq("key"),
                eq(transferIdHolder[0]), eq(HttpStatus.CREATED.value()), anyString());
        verify(auditWriter).append(eq(customerId), eq("TRANSFER_COMPLETED"), eq("TRANSFER"),
                eq(transferIdHolder[0]), any());
    }

    private final UUID[] transferIdHolder = new UUID[1];

    @Test
    void transferPersistsInsufficientFundsAsFailedAndDoesNotPostLedger() {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        when(transferRepository.claimIdempotency(eq(customerId), eq("WALLET_TRANSFER"), eq("low-funds"), anyString()))
                .thenReturn(true);
        when(walletLockService.lockWallets(any())).thenReturn(List.of(
                new LockedWallet(sourceId, customerId, "XAF", sourceAccountId, "ACTIVE"),
                new LockedWallet(destinationId, UUID.randomUUID(), "XAF", UUID.randomUUID(), "ACTIVE")));
        when(ledgerBalanceService.getBalance(sourceAccountId)).thenReturn(new BigDecimal("2"));
        when(transferRepository.transitionPending(any(UUID.class),
                eq(com.example.crossborder.transfer.domain.TransferState.FAILED),
                eq("INSUFFICIENT_FUNDS"))).thenReturn(true);
        when(transferRepository.findTransfer(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                new TransferRecord(invocation.getArgument(0), customerId, sourceId, destinationId,
                        new BigDecimal("3"), "XAF", BigDecimal.ZERO,
                        com.example.crossborder.transfer.domain.TransferState.FAILED,
                        Instant.parse("2026-01-01T00:00:00Z"), null)));

        var result = transferService.transfer(customerId, "low-funds",
                new CreateTransferRequest(sourceId, destinationId, new BigDecimal("3")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, result.status());
        assertEquals("FAILED", result.body().state());
        verify(ledgerPostingService, never()).postTransaction(any(), anyString(), any());
    }

    @Test
    void riskFlagPersistsFlaggedTransferWithoutMovingMoney() {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(transferRepository.claimIdempotency(eq(customerId), eq("WALLET_TRANSFER"), eq("flag-key"), anyString()))
                .thenReturn(true);
        when(walletLockService.lockWallets(List.of(sourceId, destinationId))).thenReturn(List.of(
                new LockedWallet(sourceId, customerId, "NGN", UUID.randomUUID(), "ACTIVE"),
                new LockedWallet(destinationId, UUID.randomUUID(), "NGN", UUID.randomUUID(), "ACTIVE")));
        when(transferRepository.transitionPending(any(UUID.class),
                eq(com.example.crossborder.transfer.domain.TransferState.FLAGGED), eq("SINGLE_TRANSFER_THRESHOLD")))
                .thenReturn(true);
        when(transferRepository.findTransfer(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                new TransferRecord(invocation.getArgument(0), customerId, sourceId, destinationId,
                        new BigDecimal("600000.00"), "NGN", BigDecimal.ZERO,
                        com.example.crossborder.transfer.domain.TransferState.FLAGGED,
                        Instant.parse("2026-01-01T00:00:00Z"), null)));
        org.mockito.Mockito.doAnswer(invocation -> {
            transferIdHolder[0] = invocation.getArgument(0);
            return null;
        }).when(transferRepository).insertTransfer(any(UUID.class), eq(customerId), eq(sourceId),
                eq(destinationId), eq(60_000_000L), eq("NGN"));
        when(riskEvaluationService.evaluate(any(UUID.class), eq(customerId), any(BigDecimal.class), eq("NGN")))
                .thenReturn(new RiskDecision(RiskDecision.Outcome.FLAG, List.of("SINGLE_TRANSFER_THRESHOLD")));

        var result = transferService.transfer(customerId, "flag-key",
                new CreateTransferRequest(sourceId, destinationId, new BigDecimal("600000.00")));

        assertEquals(HttpStatus.ACCEPTED, result.status());
        assertEquals("FLAGGED", result.body().state());
        verify(ledgerPostingService, never()).postTransaction(any(), anyString(), any());
    }

    @Test
    void sameKeyAndPayloadReplaysStoredResponseWithoutLockingWallets() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        TransferResponse response = new TransferResponse(transferId, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1.00"), "NGN", BigDecimal.ZERO, "COMPLETED", null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String body = mapper.writeValueAsString(response);
        transferService = new TransferService(transferRepository, walletLockService, ledgerBalanceService,
                ledgerPostingService, auditWriter, fxQuoteService, riskEvaluationService, mapper);
        UUID sourceId = response.sourceWalletId();
        UUID destinationId = response.destinationWalletId();
        String hash = hash(new CreateTransferRequest(sourceId, destinationId, new BigDecimal("1.00")));
        when(transferRepository.claimIdempotency(customerId, "WALLET_TRANSFER", "repeat", hash)).thenReturn(false);
        when(transferRepository.findIdempotency(customerId, "WALLET_TRANSFER", "repeat"))
                .thenReturn(Optional.of(new IdempotencyRecord(UUID.randomUUID(), hash, transferId,
                        HttpStatus.CREATED.value(), body)));

        var result = transferService.transfer(customerId, "repeat",
                new CreateTransferRequest(sourceId, destinationId, new BigDecimal("1.0")));

        assertEquals(response, result.body());
        assertEquals(HttpStatus.CREATED, result.status());
        verify(walletLockService, never()).lockWallets(any());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() {
        UUID customerId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(transferRepository.claimIdempotency(eq(customerId), eq("WALLET_TRANSFER"), eq("repeat"), anyString()))
                .thenReturn(false);
        when(transferRepository.findIdempotency(customerId, "WALLET_TRANSFER", "repeat"))
                .thenReturn(Optional.of(new IdempotencyRecord(UUID.randomUUID(), "different-hash",
                        UUID.randomUUID(), HttpStatus.CREATED.value(), "{}")));

        assertThrows(ApiException.class, () -> transferService.transfer(customerId, "repeat",
                new CreateTransferRequest(sourceId, destinationId, BigDecimal.ONE)));
    }

    @Test
    void transferRejectsInvalidAmountsBeforeClaimingIdempotency() {
        assertThrows(ApiException.class, () -> transferService.transfer(UUID.randomUUID(), "key",
                new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO)));
        verify(transferRepository, never()).claimIdempotency(any(), anyString(), anyString(), anyString());
    }

    @Test
    void conversionConsumesQuoteAndPostsSourceAndDestinationFeeTransactions() {
        UUID customerId = UUID.randomUUID();
        UUID sourceWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        UUID sourceAccount = UUID.randomUUID();
        UUID destinationAccount = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        FxQuote quote = new FxQuote(quoteId, "NGN", "XAF", new BigDecimal("10000.00"),
                new BigDecimal("4096"), new BigDecimal("4158"), new BigDecimal("100.00"),
                new BigDecimal("62"), new BigDecimal("0.42"), new BigDecimal("0.4096"),
                Instant.parse("2026-01-01T00:01:00Z"));
        when(transferRepository.claimIdempotency(eq(customerId), eq("FX_CONVERSION"), eq("convert-key"), anyString()))
                .thenReturn(true);
        when(walletLockService.lockWallets(List.of(sourceWalletId, destinationWalletId))).thenReturn(List.of(
                new LockedWallet(sourceWalletId, customerId, "NGN", sourceAccount, "ACTIVE"),
                new LockedWallet(destinationWalletId, UUID.randomUUID(), "XAF", destinationAccount, "ACTIVE")));
        when(fxQuoteService.consumeQuote(customerId, quoteId)).thenReturn(quote);
        when(ledgerBalanceService.getBalance(sourceAccount)).thenReturn(new BigDecimal("20000.00"));
        when(fxQuoteService.getSystemAccount("NGN", "FX_CLEARING")).thenReturn(UUID.randomUUID());
        when(fxQuoteService.getSystemAccount("XAF", "FX_CLEARING")).thenReturn(UUID.randomUUID());
        when(fxQuoteService.getSystemAccount("NGN", "SYSTEM_FEE")).thenReturn(UUID.randomUUID());
        when(fxQuoteService.getSystemAccount("XAF", "SYSTEM_FEE")).thenReturn(UUID.randomUUID());
        when(transferRepository.transitionPending(any(UUID.class),
                eq(com.example.crossborder.transfer.domain.TransferState.COMPLETED), eq(null))).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            transferIdHolder[0] = invocation.getArgument(0);
            return null;
        }).when(transferRepository).insertConversion(any(UUID.class), eq(customerId), eq(sourceWalletId),
                eq(destinationWalletId), eq(1_000_000L), eq(4096L), eq("NGN"), eq("XAF"), eq(10_000L), eq(quoteId));
        when(transferRepository.findTransfer(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                new TransferRecord(invocation.getArgument(0), customerId, sourceWalletId, destinationWalletId,
                        new BigDecimal("10000.00"), "NGN", new BigDecimal("4096"), "XAF",
                        new BigDecimal("100.00"), quoteId,
                        com.example.crossborder.transfer.domain.TransferState.COMPLETED,
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"))));

        var result = transferService.convert(customerId, "convert-key",
                new ConvertTransferRequest(sourceWalletId, destinationWalletId, quoteId));

        assertEquals(HttpStatus.CREATED, result.status());
        assertEquals(new BigDecimal("4096"), result.body().destinationAmount());
        assertEquals("XAF", result.body().destinationCurrency());
        assertEquals(quoteId, result.body().quoteId());
        verify(ledgerPostingService).postTransaction(eq(transferIdHolder[0]), anyString(),
                org.mockito.ArgumentMatchers.argThat(entries -> entries.size() == 3
                        && entries.stream().anyMatch(entry -> entry.creditAmount().compareTo(new BigDecimal("100.00")) == 0)));
        verify(ledgerPostingService).postTransaction(eq(transferIdHolder[0]), anyString(),
                org.mockito.ArgumentMatchers.argThat(entries -> entries.size() == 3
                        && entries.stream().anyMatch(entry -> entry.creditAmount().compareTo(new BigDecimal("62")) == 0)));
    }

    private String hash(CreateTransferRequest request) throws Exception {
        String canonical = request.sourceWalletId() + "|" + request.destinationWalletId() + "|"
                + request.amount().stripTrailingZeros().toPlainString();
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}