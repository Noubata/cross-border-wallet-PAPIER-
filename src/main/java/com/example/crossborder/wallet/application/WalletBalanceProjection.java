package com.example.crossborder.wallet.application;

import com.example.crossborder.ledger.api.LedgerBalanceChange;
import com.example.crossborder.ledger.api.LedgerTransactionPosted;
import com.example.crossborder.wallet.infrastructure.WalletRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WalletBalanceProjection {

    private final WalletRepository walletRepository;

    public WalletBalanceProjection(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT)
    @Transactional(propagation = Propagation.MANDATORY)
    public void onLedgerTransactionPosted(LedgerTransactionPosted event) {
        for (LedgerBalanceChange movement : event.movements()) {
            walletRepository.applyLedgerDelta(movement.accountId(), movement.balanceDelta());
        }
    }
}