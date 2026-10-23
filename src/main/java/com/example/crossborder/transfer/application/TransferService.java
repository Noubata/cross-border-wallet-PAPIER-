package com.example.crossborder.transfer.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.fx.api.FxQuote;
import com.example.crossborder.fx.api.FxQuoteService;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.ledger.api.LedgerCurrencyRules;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.transfer.api.CreateTransferRequest;
import com.example.crossborder.transfer.api.ConvertTransferRequest;
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
    private static final String CONVERSION_OPERATION = "FX_CONVERSION";

    private final TransferRepository transferRepository;
    private final WalletLockService walletLockService;
    private final LedgerBalanceService ledgerBalanceService;
    private final LedgerPostingService ledgerPostingService;
    private final AuditWriter auditWriter;
    private final FxQuoteService fxQuoteService;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transferRepository,
                           WalletLockService walletLockService,
                           LedgerBalanceService ledgerBalanceService,
                           LedgerPostingService ledgerPostingService,
                           AuditWriter auditWriter,
                           FxQuoteService fxQuoteService,
                           ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.walletLockService = walletLockService;
        this.ledgerBalanceService = ledgerBalanceService;
        this.ledgerPostingService = ledgerPostingService;
        this.auditWriter = auditWriter;
        this.fxQuoteService = fxQuoteService;
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
                return persistResponse(customerId, OPERATION, idempotencyKey, transferId, HttpStatus.UNPROCESSABLE_ENTITY,
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
        return persistResponse(customerId, OPERATION, idempotencyKey, transferId, HttpStatus.CREATED,
                "TRANSFER_COMPLETED");
    }

        @Transactional
        public TransferResult convert(UUID customerId, String idempotencyKey, ConvertTransferRequest request) {
        validateConversionRequest(customerId, idempotencyKey, request);
        String requestHash = hashConversionRequest(request);
        if (!transferRepository.claimIdempotency(customerId, CONVERSION_OPERATION, idempotencyKey, requestHash)) {
            IdempotencyRecord existing = transferRepository.findIdempotency(
                    customerId, CONVERSION_OPERATION, idempotencyKey)
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
        if (!source.customerId().equals(customerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found");
        }
        if (!source.status().equals("ACTIVE") || !destination.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.not_active");
        }

        FxQuote quote = fxQuoteService.consumeQuote(customerId, request.quoteId());
        if (!source.currency().equals(quote.sourceCurrency())
            || !destination.currency().equals(quote.destinationCurrency())) {
            throw new ApiException(HttpStatus.CONFLICT, "fx.quote_wallet_mismatch");
        }

        long sourceMinor = toMinorUnits(quote.sourceAmount(), quote.sourceCurrency());
        long destinationMinor = toMinorUnits(quote.destinationAmount(), quote.destinationCurrency());
        long grossDestinationMinor = toMinorUnits(quote.grossDestinationAmount(), quote.destinationCurrency());
        long flatFeeMinor = toNonnegativeMinorUnits(quote.flatFee(), quote.sourceCurrency());
        long spreadFeeMinor = toNonnegativeMinorUnits(quote.spreadFee(), quote.destinationCurrency());
        if (grossDestinationMinor != Math.addExact(destinationMinor, spreadFeeMinor)
            || sourceMinor <= flatFeeMinor) {
            throw new IllegalStateException("Stored FX quote amounts are inconsistent");
        }

        UUID transferId = UUID.randomUUID();
        transferRepository.insertConversion(transferId, customerId, source.id(), destination.id(),
            sourceMinor, destinationMinor, source.currency(), destination.currency(), flatFeeMinor, quote.id());
        BigDecimal sourceBalance = ledgerBalanceService.getBalance(source.ledgerAccountId());
        if (sourceBalance.compareTo(quote.sourceAmount()) < 0) {
            transitionOrThrow(transferId, TransferState.FAILED, "INSUFFICIENT_FUNDS");
            return persistResponse(customerId, CONVERSION_OPERATION, idempotencyKey, transferId,
                HttpStatus.UNPROCESSABLE_ENTITY, "FX_CONVERSION_INSUFFICIENT_FUNDS");
        }

        postConversionLedgerEntries(transferId, source, destination, quote,
            sourceMinor, destinationMinor, grossDestinationMinor, flatFeeMinor, spreadFeeMinor);
        TransferState.PENDING.transitionTo(TransferState.COMPLETED);
        transitionOrThrow(transferId, TransferState.COMPLETED, null);
        return persistResponse(customerId, CONVERSION_OPERATION, idempotencyKey, transferId,
            HttpStatus.CREATED, "FX_CONVERSION_COMPLETED");
        }

        private void postConversionLedgerEntries(UUID transferId, LockedWallet source, LockedWallet destination,
                             FxQuote quote, long sourceMinor, long destinationMinor,
                             long grossDestinationMinor, long flatFeeMinor,
                             long spreadFeeMinor) {
        UUID sourceClearing = fxQuoteService.getSystemAccount(source.currency(), "FX_CLEARING");
        UUID destinationClearing = fxQuoteService.getSystemAccount(destination.currency(), "FX_CLEARING");
        UUID sourceFee = fxQuoteService.getSystemAccount(source.currency(), "SYSTEM_FEE");
        UUID destinationFee = fxQuoteService.getSystemAccount(destination.currency(), "SYSTEM_FEE");

        List<LedgerEntryRequest> sourceEntries = new java.util.ArrayList<>();
        sourceEntries.add(new LedgerEntryRequest(source.ledgerAccountId(), quote.sourceAmount(), BigDecimal.ZERO));
        if (flatFeeMinor > 0) {
            sourceEntries.add(new LedgerEntryRequest(sourceFee, BigDecimal.ZERO,
                LedgerCurrencyRules.fromMinorUnits(flatFeeMinor, source.currency())));
        }
        sourceEntries.add(new LedgerEntryRequest(sourceClearing, BigDecimal.ZERO,
            LedgerCurrencyRules.fromMinorUnits(sourceMinor - flatFeeMinor, source.currency())));
        ledgerPostingService.postTransaction(transferId, "FX conversion source " + transferId, sourceEntries);

        List<LedgerEntryRequest> destinationEntries = new java.util.ArrayList<>();
        destinationEntries.add(new LedgerEntryRequest(destinationClearing,
            LedgerCurrencyRules.fromMinorUnits(grossDestinationMinor, destination.currency()), BigDecimal.ZERO));
        destinationEntries.add(new LedgerEntryRequest(destination.ledgerAccountId(), BigDecimal.ZERO,
            LedgerCurrencyRules.fromMinorUnits(destinationMinor, destination.currency())));
        if (spreadFeeMinor > 0) {
            destinationEntries.add(new LedgerEntryRequest(destinationFee, BigDecimal.ZERO,
                LedgerCurrencyRules.fromMinorUnits(spreadFeeMinor, destination.currency())));
        }
        ledgerPostingService.postTransaction(transferId, "FX conversion destination " + transferId,
            destinationEntries);
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

    private String hashConversionRequest(ConvertTransferRequest request) {
        String canonical = request.sourceWalletId() + "|" + request.destinationWalletId() + "|" + request.quoteId();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateConversionRequest(UUID customerId, String idempotencyKey,
                                           ConvertTransferRequest request) {
        if (customerId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128 || request == null || request.sourceWalletId() == null
                || request.destinationWalletId() == null || request.quoteId() == null
                || request.sourceWalletId().equals(request.destinationWalletId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "transfer.request_invalid");
        }
    }

    private long toNonnegativeMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.amount_invalid");
        }
        return amount.signum() == 0 ? 0 : toMinorUnits(amount, currency);
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

    private TransferResult persistResponse(UUID customerId, String operation, String key, UUID transferId,
                                           HttpStatus status, String auditAction) {
        TransferRecord record = transferRepository.findTransfer(transferId)
                .orElseThrow(() -> new IllegalStateException("Created transfer could not be reloaded"));
        String failureCode = record.state() == TransferState.FAILED ? "INSUFFICIENT_FUNDS" : null;
        TransferResponse response = toResponse(record, failureCode);
        String responseBody = serialize(response);
        transferRepository.completeIdempotency(customerId, operation, key, transferId, status.value(), responseBody);
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
        HttpStatus status = record.responseStatus() == null
            ? (response.state().equals(TransferState.FAILED.name())
            ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CREATED)
            : HttpStatus.valueOf(record.responseStatus());
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
            record.createdAt(), record.completedAt(), record.destinationAmount(),
            record.destinationCurrency(), record.quoteId());
    }
}