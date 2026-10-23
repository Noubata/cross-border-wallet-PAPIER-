package com.example.crossborder.wallet.api;

import java.util.List;
import java.util.UUID;

public interface WalletLockService {
    List<LockedWallet> lockWallets(List<UUID> walletIds);
}