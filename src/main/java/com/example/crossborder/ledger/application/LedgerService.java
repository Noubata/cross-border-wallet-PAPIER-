package com.example.crossborder.ledger.application;

import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerBalanceChange;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.ledger.api.LedgerTransactionPosted;
import com.example.crossborder.ledger.domain.LedgerAccount;
import com.example.crossborder.ledger.domain.LedgerCurrency;
import com.example.crossborder.ledger.infrastructure.LedgerRepository;
import com.example.crossborder.shared.error.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService implements LedgerAccountService, LedgerBalanceService, LedgerPostingService {

    private final LedgerRepository ledgerRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LedgerService(LedgerRepository ledgerRepository, ApplicationEventPublisher eventPublisher) {
        this.ledgerRepository = ledgerRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UUID createAccount(String accountType, String currencyCode) {
        if (accountType == null || accountType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.account_type_invalid");
        }
        LedgerCurrency currency = parseCurrency(currencyCode);
        return ledgerRepository.createAccount(accountType.trim(), currency);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID accountId) {
        LedgerAccount account = findAccount(accountId);
        return account.currency().fromMinorUnits(ledgerRepository.getBalanceMinor(accountId));
    }

    @Override
    @Transactional
    public UUID postTransaction(UUID operationId, String description, List<LedgerEntryRequest> entries) {
        if (operationId == null || description == null || description.isBlank()
                || description.length() > 240 || entries == null || entries.size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.transaction_invalid");
        }

        List<PreparedEntry> preparedEntries = new ArrayList<>(entries.size());
        LedgerCurrency currency = null;
        long debitTotal = 0;
        long creditTotal = 0;
        List<LedgerBalanceChange> movements = new ArrayList<>(entries.size());

        for (LedgerEntryRequest entry : entries) {
            if (entry == null || entry.accountId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.entry_invalid");
            }
            LedgerAccount account = findAccount(entry.accountId());
            if (currency == null) {
                currency = account.currency();
            } else if (currency != account.currency()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.currency_mismatch");
            }

            long debitMinor = amountToMinor(entry.debitAmount(), account.currency());
            long creditMinor = amountToMinor(entry.creditAmount(), account.currency());
            if ((debitMinor == 0) == (creditMinor == 0)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.entry_invalid");
            }
            try {
                debitTotal = Math.addExact(debitTotal, debitMinor);
                creditTotal = Math.addExact(creditTotal, creditMinor);
            } catch (ArithmeticException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.amount_out_of_range");
            }

            preparedEntries.add(new PreparedEntry(account.id(), debitMinor, creditMinor));
            movements.add(new LedgerBalanceChange(account.id(), debitMinor, creditMinor));
        }

        if (debitTotal == 0 || debitTotal != creditTotal) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.transaction_unbalanced");
        }

        UUID transactionId = UUID.randomUUID();
        ledgerRepository.insertTransaction(transactionId, operationId, currency, description.trim());
        for (PreparedEntry entry : preparedEntries) {
            ledgerRepository.insertEntry(UUID.randomUUID(), transactionId, entry.accountId(),
                    entry.debitMinor(), entry.creditMinor());
        }
        eventPublisher.publishEvent(new LedgerTransactionPosted(transactionId, operationId,
                currency.name(), List.copyOf(movements)));
        return transactionId;
    }

    private LedgerAccount findAccount(UUID accountId) {
        return ledgerRepository.findAccount(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ledger.account_not_found"));
    }

    private long amountToMinor(BigDecimal amount, LedgerCurrency currency) {
        if (amount == null || amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.entry_invalid");
        }
        if (amount.signum() == 0) {
            return 0;
        }
        try {
            return currency.toMinorUnits(amount);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.amount_invalid");
        }
    }

    private LedgerCurrency parseCurrency(String currencyCode) {
        try {
            return LedgerCurrency.fromCode(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ledger.currency_invalid");
        }
    }

    private record PreparedEntry(UUID accountId, long debitMinor, long creditMinor) {
    }
}