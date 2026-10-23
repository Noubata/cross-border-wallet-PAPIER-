package com.example.crossborder.funding.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.funding.api.CreateFundingRequest;
import com.example.crossborder.funding.api.FundingPaymentView;
import com.example.crossborder.funding.api.GatewayInitialization;
import com.example.crossborder.funding.api.PaymentGateway;
import com.example.crossborder.funding.api.WebhookResult;
import com.example.crossborder.funding.domain.FundingPayment;
import com.example.crossborder.funding.infrastructure.FundingRepository;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerCurrencyRules;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.wallet.api.LockedWallet;
import com.example.crossborder.wallet.api.WalletLockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FundingService {

    private final PaymentGateway paymentGateway;
    private final FundingRepository fundingRepository;
    private final WalletLockService walletLockService;
    private final LedgerAccountService ledgerAccountService;
    private final LedgerPostingService ledgerPostingService;
    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;

    public FundingService(PaymentGateway paymentGateway, FundingRepository fundingRepository,
                          WalletLockService walletLockService, LedgerAccountService ledgerAccountService,
                          LedgerPostingService ledgerPostingService, AuditWriter auditWriter,
                          ObjectMapper objectMapper) {
        this.paymentGateway = paymentGateway;
        this.fundingRepository = fundingRepository;
        this.walletLockService = walletLockService;
        this.ledgerAccountService = ledgerAccountService;
        this.ledgerPostingService = ledgerPostingService;
        this.auditWriter = auditWriter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FundingPaymentView createPayment(UUID customerId, CreateFundingRequest request) {
        if (customerId == null || request == null || request.walletId() == null
                || request.email() == null || request.email().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "funding.request_invalid");
        }
        long amountMinor = toMinorUnits(request.amount(), request.currency());
        List<LockedWallet> wallets = walletLockService.lockWallets(List.of(request.walletId()));
        LockedWallet wallet = wallets.getFirst();
        if (!wallet.customerId().equals(customerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found");
        }
        if (!wallet.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.not_active");
        }
        if (!wallet.currency().equals(request.currency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "funding.currency_mismatch");
        }

        String reference = UUID.randomUUID().toString();
        GatewayInitialization initialization = paymentGateway.initializePayment(
                reference, amountMinor, request.currency(), request.email());
        FundingPayment payment = new FundingPayment(UUID.randomUUID(), customerId, wallet.id(),
                wallet.ledgerAccountId(), paymentGateway.gatewayName(), initialization.reference(),
                amountMinor, request.currency(), "PENDING", initialization.authorizationUrl(),
                java.time.Instant.now(), null);
        fundingRepository.insertPayment(payment);
        auditWriter.append(customerId, "FUNDING_PAYMENT_CREATED", "FUNDING_PAYMENT", payment.id(),
                Map.of("reference", payment.reference(), "amountMinor", amountMinor,
                        "currency", payment.currency(), "gateway", payment.gateway()));
        return toView(payment);
    }

    @Transactional
    public WebhookResult handleWebhook(byte[] rawBody, String signature) {
        if (!paymentGateway.verifyWebhookSignature(rawBody, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "funding.signature_invalid");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "funding.webhook_invalid");
        }
        String event = payload.path("event").asText();
        JsonNode data = payload.path("data");
        String reference = data.path("reference").asText(null);
        if (reference == null || reference.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "funding.webhook_invalid");
        }
        if (!event.equals("charge.success") && !event.equals("charge.failed")) {
            return new WebhookResult(false, false);
        }

        FundingPayment payment = fundingRepository.findPaymentForUpdate(reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "funding.payment_not_found"));
        if (!payment.status().equals("PENDING")) {
            return new WebhookResult(true, true);
        }
        if (event.equals("charge.failed")) {
            fundingRepository.markFailed(reference);
            auditWriter.append(payment.customerId(), "FUNDING_PAYMENT_FAILED", "FUNDING_PAYMENT", payment.id(),
                    Map.of("reference", reference));
            return new WebhookResult(true, false);
        }

        long webhookAmount = data.path("amount").asLong(-1);
        String webhookCurrency = data.path("currency").asText("");
        if (webhookAmount != payment.amountMinor() || !webhookCurrency.equals(payment.currency())) {
            throw new ApiException(HttpStatus.CONFLICT, "funding.payment_mismatch");
        }

        List<LockedWallet> wallets = walletLockService.lockWallets(List.of(payment.walletId()));
        LockedWallet wallet = wallets.getFirst();
        if (!wallet.customerId().equals(payment.customerId()) || !wallet.status().equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "funding.wallet_unavailable");
        }
        UUID clearingAccount = getClearingAccount(payment.currency());
        BigDecimal amount = LedgerCurrencyRules.fromMinorUnits(payment.amountMinor(), payment.currency());
        UUID ledgerTransactionId = ledgerPostingService.postTransaction(payment.id(),
                "Gateway funding " + reference, List.of(
                        new LedgerEntryRequest(clearingAccount, amount, BigDecimal.ZERO),
                        new LedgerEntryRequest(wallet.ledgerAccountId(), BigDecimal.ZERO, amount)));
        if (!fundingRepository.markSucceeded(reference, ledgerTransactionId)) {
            throw new IllegalStateException("Funding payment changed state while locked");
        }
        auditWriter.append(payment.customerId(), "FUNDING_PAYMENT_SUCCEEDED", "FUNDING_PAYMENT", payment.id(),
                Map.of("reference", reference, "amountMinor", payment.amountMinor(),
                        "currency", payment.currency()));
        return new WebhookResult(true, false);
    }

    private UUID getClearingAccount(String currency) {
        return fundingRepository.findClearingAccount(currency).orElseGet(() -> {
            UUID accountId = ledgerAccountService.createAccount("FUNDING_CLEARING", currency);
            fundingRepository.insertClearingAccount(currency, accountId);
            return fundingRepository.findClearingAccount(currency).orElse(accountId);
        });
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        try {
            return LedgerCurrencyRules.toMinorUnits(amount, currency);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "funding.amount_invalid");
        }
    }

    private FundingPaymentView toView(FundingPayment payment) {
        return new FundingPaymentView(payment.id(), payment.reference(), payment.walletId(),
                LedgerCurrencyRules.fromMinorUnits(payment.amountMinor(), payment.currency()),
                payment.currency(), payment.status(), payment.authorizationUrl(),
                payment.createdAt(), payment.completedAt());
    }
}