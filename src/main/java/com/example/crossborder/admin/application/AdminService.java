package com.example.crossborder.admin.application;

import com.example.crossborder.admin.infrastructure.AdminRepository;
import com.example.crossborder.audit.api.AuditEventView;
import com.example.crossborder.audit.api.AuditReader;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.TransferResponse;
import com.example.crossborder.transfer.api.TransferReviewService;
import com.example.crossborder.wallet.api.WalletManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final TransferReviewService transferReviewService;
    private final WalletManagementService walletManagementService;
    private final AuditReader auditReader;
    private final AdminRepository adminRepository;

    public AdminService(TransferReviewService transferReviewService,
                        WalletManagementService walletManagementService,
                        AuditReader auditReader,
                        AdminRepository adminRepository) {
        this.transferReviewService = transferReviewService;
        this.walletManagementService = walletManagementService;
        this.auditReader = auditReader;
        this.adminRepository = adminRepository;
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> getFlaggedTransfers() {
        return transferReviewService.getFlaggedTransfers();
    }

    @Transactional
    public TransferResponse approveTransfer(UUID adminId, UUID transferId) {
        requireAdmin(adminId);
        TransferResponse response = transferReviewService.approveFlaggedTransfer(transferId, adminId);
        adminRepository.recordTransferReview(transferId, adminId, "APPROVED", null);
        return response;
    }

    @Transactional
    public TransferResponse rejectTransfer(UUID adminId, UUID transferId, String reason) {
        requireAdmin(adminId);
        if (reason != null && reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "admin.review_reason_invalid");
        }
        TransferResponse response = transferReviewService.rejectFlaggedTransfer(transferId, adminId, reason);
        adminRepository.recordTransferReview(transferId, adminId, "REJECTED", reason);
        return response;
    }

    @Transactional
    public void freezeWallet(UUID adminId, UUID walletId) {
        requireAdmin(adminId);
        walletManagementService.freezeWallet(walletId, adminId);
    }

    @Transactional
    public void unfreezeWallet(UUID adminId, UUID walletId) {
        requireAdmin(adminId);
        walletManagementService.unfreezeWallet(walletId, adminId);
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> getAuditEvents(int limit) {
        return auditReader.getRecentEvents(limit);
    }

    private void requireAdmin(UUID adminId) {
        if (adminId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "security.unauthorized");
        }
    }
}