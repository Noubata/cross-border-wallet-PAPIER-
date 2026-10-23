package com.example.crossborder.wallet.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.wallet.domain.Wallet;
import com.example.crossborder.wallet.domain.WalletCurrency;
import com.example.crossborder.wallet.infrastructure.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
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

class WalletServiceTest {

    private WalletRepository walletRepository;
    private LedgerAccountService ledgerAccountService;
    private LedgerBalanceService ledgerBalanceService;
    private AuditWriter auditWriter;
    private WalletService service;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        ledgerAccountService = mock(LedgerAccountService.class);
        ledgerBalanceService = mock(LedgerBalanceService.class);
        auditWriter = mock(AuditWriter.class);
        service = new WalletService(walletRepository, ledgerAccountService, ledgerBalanceService, auditWriter);
    }

    @Test
    void createWalletCreatesLedgerAccountAndAuditsWallet() {
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(ledgerAccountService.createAccount("WALLET", "NGN")).thenReturn(accountId);
        when(walletRepository.insert(any(UUID.class), eq(customerId), eq(WalletCurrency.NGN), eq(accountId)))
                .thenReturn(true);
        when(walletRepository.findByCustomerAndCurrency(eq(customerId), eq(WalletCurrency.NGN)))
                .thenReturn(Optional.of(new Wallet(UUID.randomUUID(), customerId, WalletCurrency.NGN,
                        accountId, 0, "ACTIVE", Instant.now())));
        when(ledgerBalanceService.getBalance(accountId)).thenReturn(BigDecimal.ZERO);

        assertEquals("NGN", service.createWallet(customerId, "NGN").currency());
        verify(auditWriter).append(eq(customerId), eq("WALLET_CREATED"), eq("WALLET"), any(UUID.class), any());
    }

    @Test
    void createWalletRejectsDuplicateCurrency() {
        UUID customerId = UUID.randomUUID();
        when(ledgerAccountService.createAccount("WALLET", "XAF")).thenReturn(UUID.randomUUID());
        when(walletRepository.insert(any(UUID.class), eq(customerId), eq(WalletCurrency.XAF), any(UUID.class)))
                .thenReturn(false);

        assertThrows(ApiException.class, () -> service.createWallet(customerId, "XAF"));
    }

    @Test
    void getWalletsReturnsCustomerWallets() {
        UUID customerId = UUID.randomUUID();
        Wallet wallet = new Wallet(UUID.randomUUID(), customerId, WalletCurrency.XAF,
                UUID.randomUUID(), 0, "ACTIVE", Instant.now());
        when(walletRepository.findAllByCustomer(customerId)).thenReturn(List.of(wallet));
        when(ledgerBalanceService.getBalance(wallet.ledgerAccountId())).thenReturn(BigDecimal.ZERO);

        assertEquals(1, service.getWallets(customerId).size());
    }

    @Test
    void getWalletReturnsLedgerDerivedBalance() {
        UUID customerId = UUID.randomUUID();
        Wallet wallet = new Wallet(UUID.randomUUID(), customerId, WalletCurrency.NGN,
                UUID.randomUUID(), 500, "ACTIVE", Instant.now());
        when(walletRepository.findByCustomerAndCurrency(customerId, WalletCurrency.NGN))
                .thenReturn(Optional.of(wallet));
        when(ledgerBalanceService.getBalance(wallet.ledgerAccountId())).thenReturn(new BigDecimal("12.34"));

        assertEquals(new BigDecimal("12.34"), service.getWallet(customerId, "NGN").balance());
    }

    @Test
    void getWalletRejectsUnsupportedCurrency() {
        assertThrows(ApiException.class, () -> service.getWallet(UUID.randomUUID(), "USD"));
    }
}