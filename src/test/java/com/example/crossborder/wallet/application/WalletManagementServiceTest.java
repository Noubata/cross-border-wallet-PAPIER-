package com.example.crossborder.wallet.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.infrastructure.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletManagementServiceTest {

    private WalletRepository walletRepository;
    private AuditWriter auditWriter;
    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletRepository = mock(WalletRepository.class);
        auditWriter = mock(AuditWriter.class);
        walletService = new WalletService(walletRepository, mock(LedgerAccountService.class),
                mock(LedgerBalanceService.class), auditWriter);
    }

    @Test
    void freezesAndUnfreezesWalletWithAuditEvents() {
        UUID walletId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(walletRepository.lockWallets(List.of(walletId))).thenReturn(
                List.of(new LockedWallet(walletId, UUID.randomUUID(), "NGN", UUID.randomUUID(), "ACTIVE")),
                List.of(new LockedWallet(walletId, UUID.randomUUID(), "NGN", UUID.randomUUID(), "FROZEN")));
        when(walletRepository.setStatus(walletId, "ACTIVE", "FROZEN")).thenReturn(true);
        when(walletRepository.setStatus(walletId, "FROZEN", "ACTIVE")).thenReturn(true);

        walletService.freezeWallet(walletId, adminId);
        walletService.unfreezeWallet(walletId, adminId);

        verify(walletRepository).setStatus(walletId, "ACTIVE", "FROZEN");
        verify(walletRepository).setStatus(walletId, "FROZEN", "ACTIVE");
        verify(auditWriter).append(eq(adminId), eq("WALLET_FROZEN"), eq("WALLET"), eq(walletId), any());
        verify(auditWriter).append(eq(adminId), eq("WALLET_UNFROZEN"), eq("WALLET"), eq(walletId), any());
    }

    @Test
    void rejectsIllegalWalletStatusTransition() {
        UUID walletId = UUID.randomUUID();
        when(walletRepository.lockWallets(List.of(walletId))).thenReturn(
                List.of(new LockedWallet(walletId, UUID.randomUUID(), "NGN", UUID.randomUUID(), "FROZEN")));

        assertThrows(ApiException.class, () -> walletService.freezeWallet(walletId, UUID.randomUUID()));
    }
}