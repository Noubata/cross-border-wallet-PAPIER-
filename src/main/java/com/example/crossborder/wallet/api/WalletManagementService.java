package com.example.crossborder.wallet.api;

import java.util.UUID;

public interface WalletManagementService {
    void freezeWallet(UUID walletId, UUID adminId);

    void unfreezeWallet(UUID walletId, UUID adminId);
}