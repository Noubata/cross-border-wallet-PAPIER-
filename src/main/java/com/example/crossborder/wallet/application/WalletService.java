package com.example.crossborder.wallet.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.wallet.api.WalletView;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.api.WalletLockService;
import com.example.crossborder.wallet.api.WalletManagementService;
import com.example.crossborder.wallet.domain.Wallet;
import com.example.crossborder.wallet.domain.WalletCurrency;
import com.example.crossborder.wallet.infrastructure.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletService implements WalletLockService, WalletManagementService {

    private final WalletRepository walletRepository;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerBalanceService ledgerBalanceService;
    private final AuditWriter auditWriter;

    public WalletService(WalletRepository walletRepository,
                         LedgerAccountService ledgerAccountService,
                         LedgerBalanceService ledgerBalanceService,
                         AuditWriter auditWriter) {
        this.walletRepository = walletRepository;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerBalanceService = ledgerBalanceService;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public WalletView createWallet(UUID customerId, String currencyCode) {
        WalletCurrency currency = parseCurrency(currencyCode);
        UUID ledgerAccountId = ledgerAccountService.createAccount("WALLET", currency.name());
        UUID walletId = UUID.randomUUID();
        if (!walletRepository.insert(walletId, customerId, currency, ledgerAccountId)) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.already_exists");
        }
        auditWriter.append(customerId, "WALLET_CREATED", "WALLET", walletId,
                Map.of("currency", currency.name()));
        return toView(walletRepository.findByCustomerAndCurrency(customerId, currency).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<WalletView> getWallets(UUID customerId) {
        return walletRepository.findAllByCustomer(customerId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public WalletView getWallet(UUID customerId, String currencyCode) {
        WalletCurrency currency = parseCurrency(currencyCode);
        Wallet wallet = walletRepository.findByCustomerAndCurrency(customerId, currency)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found"));
        return toView(wallet);
    }

    @Override
    @Transactional
    public List<LockedWallet> lockWallets(List<UUID> walletIds) {
        if (walletIds == null || walletIds.isEmpty() || walletIds.stream().anyMatch(java.util.Objects::isNull)
                || walletIds.stream().distinct().count() != walletIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wallet.lock_request_invalid");
        }
        List<UUID> orderedWalletIds = walletIds.stream().sorted().toList();
        List<LockedWallet> lockedWallets = walletRepository.lockWallets(orderedWalletIds);
        if (lockedWallets.size() != orderedWalletIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found");
        }
        return lockedWallets;
    }

    @Override
    @Transactional
    public void freezeWallet(UUID walletId, UUID adminId) {
        updateWalletStatus(walletId, "ACTIVE", "FROZEN", adminId, "WALLET_FROZEN");
    }

    @Override
    @Transactional
    public void unfreezeWallet(UUID walletId, UUID adminId) {
        updateWalletStatus(walletId, "FROZEN", "ACTIVE", adminId, "WALLET_UNFROZEN");
    }

    private void updateWalletStatus(UUID walletId, String expected, String target,
                                    UUID adminId, String auditAction) {
        List<LockedWallet> locked = walletRepository.lockWallets(List.of(walletId));
        if (locked.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found");
        }
        if (!locked.get(0).status().equals(expected)) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.status_transition_invalid");
        }
        if (!walletRepository.setStatus(walletId, expected, target)) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.status_transition_invalid");
        }
        auditWriter.append(adminId, auditAction, "WALLET", walletId, Map.of("status", target));
    }

    private WalletView toView(Wallet wallet) {
        return new WalletView(wallet.id(), wallet.currency().name(),
                ledgerBalanceService.getBalance(wallet.ledgerAccountId()), wallet.status(), wallet.createdAt());
    }

    private WalletCurrency parseCurrency(String currencyCode) {
        try {
            return WalletCurrency.fromCode(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "wallet.currency_invalid");
        }
    }
}