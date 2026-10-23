package com.example.crossborder.admin.application;

import com.example.crossborder.admin.infrastructure.AdminRepository;
import com.example.crossborder.audit.api.AuditReader;
import com.example.crossborder.audit.api.AuditEventView;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.TransferResponse;
import com.example.crossborder.transfer.api.TransferReviewService;
import com.example.crossborder.wallet.api.WalletManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceTest {

    private TransferReviewService transferReviewService;
    private WalletManagementService walletManagementService;
    private AuditReader auditReader;
    private AdminRepository adminRepository;
    private AdminService service;

    @BeforeEach
    void setUp() {
        transferReviewService = mock(TransferReviewService.class);
        walletManagementService = mock(WalletManagementService.class);
        auditReader = mock(AuditReader.class);
        adminRepository = mock(AdminRepository.class);
        service = new AdminService(transferReviewService, walletManagementService, auditReader, adminRepository);
    }

    @Test
    void listsFlaggedTransfers() {
        when(transferReviewService.getFlaggedTransfers()).thenReturn(List.of(response()));
        assertEquals(1, service.getFlaggedTransfers().size());
    }

    @Test
    void approvesTransferAndRecordsReview() {
        UUID adminId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(transferReviewService.approveFlaggedTransfer(transferId, adminId)).thenReturn(response());

        service.approveTransfer(adminId, transferId);

        verify(adminRepository).recordTransferReview(transferId, adminId, "APPROVED", null);
    }

    @Test
    void rejectsTransferAndRecordsReason() {
        UUID adminId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        when(transferReviewService.rejectFlaggedTransfer(transferId, adminId, "manual review"))
                .thenReturn(response());

        service.rejectTransfer(adminId, transferId, "manual review");

        verify(adminRepository).recordTransferReview(transferId, adminId, "REJECTED", "manual review");
    }

    @Test
    void rejectsOversizedReviewReason() {
        assertThrows(ApiException.class, () -> service.rejectTransfer(UUID.randomUUID(), UUID.randomUUID(),
                "x".repeat(501)));
    }

    @Test
    void freezesAndUnfreezesWallet() {
        UUID adminId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        service.freezeWallet(adminId, walletId);
        service.unfreezeWallet(adminId, walletId);

        verify(walletManagementService).freezeWallet(walletId, adminId);
        verify(walletManagementService).unfreezeWallet(walletId, adminId);
    }

    @Test
    void readsRecentAuditEvents() {
        when(auditReader.getRecentEvents(20)).thenReturn(List.of(new AuditEventView(
                UUID.randomUUID(), UUID.randomUUID(), "ACTION", "ENTITY", UUID.randomUUID(),
                java.util.Map.of(), Instant.now())));

        assertEquals(1, service.getAuditEvents(20).size());
        verify(auditReader).getRecentEvents(20);
    }

    private TransferResponse response() {
        return new TransferResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ONE, "NGN", BigDecimal.ZERO, "COMPLETED", null,
                Instant.now(), Instant.now());
    }
}