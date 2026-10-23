package com.example.crossborder.admin.api;

import com.example.crossborder.admin.application.AdminService;
import com.example.crossborder.audit.api.AuditEventView;
import com.example.crossborder.transfer.api.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/transfers/flagged")
    public List<TransferResponse> flaggedTransfers() {
        return adminService.getFlaggedTransfers();
    }

    @PostMapping("/transfers/{transferId}/approve")
    public TransferResponse approve(Authentication authentication, @PathVariable UUID transferId) {
        return adminService.approveTransfer(UUID.fromString(authentication.getName()), transferId);
    }

    @PostMapping("/transfers/{transferId}/reject")
    public TransferResponse reject(Authentication authentication, @PathVariable UUID transferId,
                                   @Valid @RequestBody(required = false) RejectTransferRequest request) {
        return adminService.rejectTransfer(UUID.fromString(authentication.getName()), transferId,
                request == null ? null : request.reason());
    }

    @PostMapping("/wallets/{walletId}/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void freeze(Authentication authentication, @PathVariable UUID walletId) {
        adminService.freezeWallet(UUID.fromString(authentication.getName()), walletId);
    }

    @PostMapping("/wallets/{walletId}/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreeze(Authentication authentication, @PathVariable UUID walletId) {
        adminService.unfreezeWallet(UUID.fromString(authentication.getName()), walletId);
    }

    @GetMapping("/audit")
    public List<AuditEventView> audit(@RequestParam(defaultValue = "100") int limit) {
        return adminService.getAuditEvents(limit);
    }
}