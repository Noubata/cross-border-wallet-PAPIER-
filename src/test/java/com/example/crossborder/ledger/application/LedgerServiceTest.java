package com.example.crossborder.ledger.application;

import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.domain.LedgerAccount;
import com.example.crossborder.ledger.domain.LedgerCurrency;
import com.example.crossborder.ledger.infrastructure.LedgerRepository;
import com.example.crossborder.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerServiceTest {

    private LedgerRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private LedgerService service;

    @BeforeEach
    void setUp() {
        repository = mock(LedgerRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new LedgerService(repository, eventPublisher);
    }

    @Test
    void createAccountPersistsSupportedCurrency() {
        when(repository.createAccount("WALLET", LedgerCurrency.NGN)).thenReturn(UUID.randomUUID());

        UUID accountId = service.createAccount("WALLET", "NGN");

        verify(repository).createAccount("WALLET", LedgerCurrency.NGN);
        org.junit.jupiter.api.Assertions.assertNotNull(accountId);
    }

    @Test
    void createAccountRejectsUnsupportedCurrency() {
        assertThrows(ApiException.class, () -> service.createAccount("WALLET", "USD"));
    }

    @Test
    void getBalanceReturnsTheLedgerDerivedValue() {
        UUID accountId = UUID.randomUUID();
        when(repository.findAccount(accountId)).thenReturn(Optional.of(
                new LedgerAccount(accountId, "WALLET", LedgerCurrency.NGN)));
        when(repository.getBalanceMinor(accountId)).thenReturn(12345L);

        assertEquals(new BigDecimal("123.45"), service.getBalance(accountId));
    }

    @Test
    void postTransactionPersistsBalancedMinorUnitEntriesAndPublishesEvent() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        when(repository.findAccount(debitAccount)).thenReturn(Optional.of(
                new LedgerAccount(debitAccount, "WALLET", LedgerCurrency.NGN)));
        when(repository.findAccount(creditAccount)).thenReturn(Optional.of(
                new LedgerAccount(creditAccount, "WALLET", LedgerCurrency.NGN)));
        when(repository.getBalanceMinor(any())).thenReturn(0L);

        UUID transactionId = service.postTransaction(operationId, "transfer", List.of(
                new LedgerEntryRequest(debitAccount, new BigDecimal("12.34"), BigDecimal.ZERO),
                new LedgerEntryRequest(creditAccount, BigDecimal.ZERO, new BigDecimal("12.34"))));

        verify(repository).insertTransaction(eq(transactionId), eq(operationId), eq(LedgerCurrency.NGN), eq("transfer"));
        verify(repository).insertEntry(any(UUID.class), eq(transactionId), eq(debitAccount), eq(1234L), eq(0L));
        verify(repository).insertEntry(any(UUID.class), eq(transactionId), eq(creditAccount), eq(0L), eq(1234L));
        verify(eventPublisher).publishEvent(any(com.example.crossborder.ledger.api.LedgerTransactionPosted.class));
    }

    @Test
    void postTransactionRejectsUnbalancedEntriesBeforeWriting() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        when(repository.findAccount(any(UUID.class))).thenAnswer(invocation -> {
            UUID accountId = invocation.getArgument(0);
            return Optional.of(new LedgerAccount(accountId, "WALLET", LedgerCurrency.NGN));
        });

        assertThrows(ApiException.class, () -> service.postTransaction(UUID.randomUUID(), "bad", List.of(
                new LedgerEntryRequest(debitAccount, new BigDecimal("10.00"), BigDecimal.ZERO),
                new LedgerEntryRequest(creditAccount, BigDecimal.ZERO, new BigDecimal("9.00")))));
        verify(repository, org.mockito.Mockito.never()).insertTransaction(any(), any(), any(), anyString());
    }

    @Test
    void postTransactionRejectsCurrencyMismatch() {
        UUID ngnAccount = UUID.randomUUID();
        UUID xafAccount = UUID.randomUUID();
        when(repository.findAccount(ngnAccount)).thenReturn(Optional.of(
                new LedgerAccount(ngnAccount, "WALLET", LedgerCurrency.NGN)));
        when(repository.findAccount(xafAccount)).thenReturn(Optional.of(
                new LedgerAccount(xafAccount, "WALLET", LedgerCurrency.XAF)));

        assertThrows(ApiException.class, () -> service.postTransaction(UUID.randomUUID(), "bad", List.of(
                new LedgerEntryRequest(ngnAccount, new BigDecimal("1.00"), BigDecimal.ZERO),
                new LedgerEntryRequest(xafAccount, BigDecimal.ZERO, BigDecimal.ONE))));
    }

    @Test
    void postTransactionRejectsXafFractions() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        when(repository.findAccount(any(UUID.class))).thenAnswer(invocation -> Optional.of(
                new LedgerAccount(invocation.getArgument(0), "WALLET", LedgerCurrency.XAF)));

        assertThrows(ApiException.class, () -> service.postTransaction(UUID.randomUUID(), "fraction", List.of(
                new LedgerEntryRequest(debitAccount, new BigDecimal("1.5"), BigDecimal.ZERO),
                new LedgerEntryRequest(creditAccount, BigDecimal.ZERO, new BigDecimal("1.5")))));
    }
}