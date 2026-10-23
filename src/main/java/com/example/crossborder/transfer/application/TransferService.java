package com.example.crossborder.transfer.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerCurrencyRules;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.CreateTransferRequest;
import com.example.crossborder.transfer.api.TransferResponse;
import com.example.crossborder.transfer.api.TransferResult;
import com.example.crossborder.transfer.domain.IdempotencyRecord;
import com.example.crossborder.transfer.domain.TransferRecord;
import com.example.crossborder.transfer.domain.TransferState;
import com.example.crossborder.transfer.infrastructure.TransferRepository;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.api.WalletLockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private static final String OPERATION = "WALLET_TRANSFER";

    private final TransferRepository transferRepository;
    private final WalletLockService walletLockService;
    private final LedgerBalanceService ledgerBalanceService;
    private final LedgerPostingService ledgerPostingService;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transferRepository,
                           WalletLockService walletLockService,
                           LedgerBalanceService ledgerBalanceService,
                           LedgerPostingService ledgerPostingService,
                           AuditWriter auditWriter,
                           ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.walletLockService = walletLockService;
        this.ledgerBalanceService = ledgerBalanceService;
        this.ledgerPostingService = ledgerPostingService;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferResult transfer(UUID customerId, String idempotencyKey, CreateTransferRequest request) {
        validateRequest(customerId, idempotencyKey, request);
        String requestHash = hashRequest(request);

        if (!transferRepository.claimIdempotency(customerId, OPERATION, idempotencyKey, requestHash)) {
            IdempotencyRecord existing = transferRepository.findIdempotency(customerId, OPERATION, idempotencyKey)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "transfer.idempotency_conflict"));
            if (!MessageDigest.isEqual(existing.requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw new ApiException(HttpStatus.CONFLICT, "transfer.idempotency_conflict");
            }
            if (existing.responseBody() == null) {
                throw new ApiException(HttpStatus.CONFLICT, "transfer.idempotency_in_progress");
            }
            return replay(existing);
        }

        List<LockedWallet> wallets = walletLockService.lockWallets(
                List.of(request.sourceWalletId(), request.destinationWalletId()));
        LockedWallet source = findWallet(wallets, request.sourceWalletId());
        LockedWallet destination = findWallet(wallets, request.destinationWalletId());
        validateWallets(customerId, source, destination);

        String currency = source.currency();
        long amountMinor = toMinorUnits(request.amount(), currency);
        UUID transferId = UUID.randomUUID();
        transferRepository.insertTransfer(transferId, customerId, source.id(), destination.id(),
            amountMinor, currency);

        BigDecimal sourceBalance = ledgerBalanceService.getBalance(source.ledgerAccountId());
        if (sourceBalance.compareTo(LedgerCurrencyRules.fromMinorUnits(amountMinor, currency)) < 0) {
            TransferState.PENDING.transitionTo(TransferState.FAILED);
            transitionOrThrow(transferId, TransferState.FAILED, "INSUFFICIENT_FUNDS");
            return persistResponse(customerId, idempotencyKey, transferId, HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRANSFER_INSUFFICIENT_FUNDS");
        }

        UUID operationId = transferId;
        ledgerPostingService.postTransaction(operationId, "Wallet transfer " + transferId, List.of(
            new LedgerEntryRequest(source.ledgerAccountId(),
                LedgerCurrencyRules.fromMinorUnits(amountMinor, currency), BigDecimal.ZERO),
            new LedgerEntryRequest(destination.ledgerAccountId(), BigDecimal.ZERO,
                LedgerCurrencyRules.fromMinorUnits(amountMinor, currency))));
        TransferState.PENDING.transitionTo(TransferState.COMPLETED);
        transitionOrThrow(transferId, TransferState.COMPLETED, null);
        return persistResponse(customerId, idempotencyKey, transferId, HttpStatus.CREATED,
                "TRANSFER_COMPLETED");
    }

    private void validateRequest(UUID customerId, String idempotencyKey, CreateTransferRequest request) {
        if (customerId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128 || request == null
                || request.sourceWalletId() == null || request.destinationWalletId() == null
                || request.sourceWalletId().equals(request.destinationWalletId())
                || request.amount() == null || request.amount().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transfer.request_invalid");
        }
    }

    private String hashRequest(CreateTransferRequest request) {
        String canonical = request.sourceWalletId() + "|" + request.destinationWalletId() + "|"
                + request.amount().stripTrailingZeros().toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private List<LockedWallet> validateWallets(UUID customerId, LockedWallet source, LockedWallet destination) {
        if (!source.customerId().equals(customerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found");
        }
        if (!source.status().equals("ACTIVE") || !destination.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.not_active");
        }
        if (!source.currency().equals(destination.currency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transfer.currency_mismatch");
        }
        return List.of(source, destination);
    }

    private LockedWallet findWallet(List<LockedWallet> wallets, UUID walletId) {
        return wallets.stream().filter(wallet -> wallet.id().equals(walletId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found"));
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        try {
            return LedgerCurrencyRules.toMinorUnits(amount, currency);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transfer.amount_invalid");
        }
    }

    private void transitionOrThrow(UUID transferId, TransferState target, String failureCode) {
        if (!transferRepository.transitionPending(transferId, target, failureCode)) {
            throw new IllegalStateException("Illegal transfer state transition to " + target);
        }
    }

    private TransferResult persistResponse(UUID customerId, String key, UUID transferId,
                                           HttpStatus status, String auditAction) {
        TransferRecord record = transferRepository.findTransfer(transferId)
                .orElseThrow(() -> new IllegalStateException("Created transfer could not be reloaded"));
        String failureCode = record.state() == TransferState.FAILED ? "INSUFFICIENT_FUNDS" : null;
        TransferResponse response = toResponse(record, failureCode);
        String responseBody = serialize(response);
        transferRepository.completeIdempotency(customerId, OPERATION, key, transferId, responseBody);
        auditWriter.append(customerId, auditAction, "TRANSFER", transferId,
                Map.of("sourceWalletId", record.sourceWalletId().toString(),
                        "destinationWalletId", record.destinationWalletId().toString(),
                        "amount", record.amount().toPlainString(), "currency", record.currency()));
        return new TransferResult(status, response);
    }

    private TransferResult replay(IdempotencyRecord record) {
        TransferResponse response;
        try {
            response = objectMapper.readValue(record.responseBody(), TransferResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored idempotency response is invalid", exception);
        }
        HttpStatus status = response.state().equals(TransferState.FAILED.name())
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED;
        return new TransferResult(status, response);
    }

    private String serialize(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Transfer response could not be serialized", exception);
        }
    }

    private TransferResponse toResponse(TransferRecord record, String failureCode) {
        return new TransferResponse(record.id(), record.sourceWalletId(), record.destinationWalletId(),
                record.amount(), record.currency(), record.fee(), record.state().name(), failureCode,
                record.createdAt(), record.completedAt());
    }
}