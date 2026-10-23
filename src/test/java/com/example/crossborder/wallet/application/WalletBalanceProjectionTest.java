package com.example.crossborder.wallet.application;

import com.example.crossborder.ledger.api.LedgerBalanceChange;
import com.example.crossborder.ledger.api.LedgerTransactionPosted;
import com.example.crossborder.wallet.infrastructure.WalletRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WalletBalanceProjectionTest {

    @Test
    void appliesCreditAndDebitDeltasToWalletCache() {
        WalletRepository repository = mock(WalletRepository.class);
        WalletBalanceProjection projection = new WalletBalanceProjection(repository);
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();

        projection.onLedgerTransactionPosted(new LedgerTransactionPosted(UUID.randomUUID(), UUID.randomUUID(), "NGN",
                List.of(new LedgerBalanceChange(debitAccount, 1200, 0),
                    new LedgerBalanceChange(creditAccount, 0, 1200))));

        verify(repository).applyLedgerDelta(debitAccount, -1200);
        verify(repository).applyLedgerDelta(creditAccount, 1200);
    }
}