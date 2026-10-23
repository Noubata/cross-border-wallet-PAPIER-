package com.example.crossborder.fx.application;

import com.example.crossborder.fx.api.FxQuote;
import com.example.crossborder.fx.api.FxQuoteService;
import com.example.crossborder.fx.api.RateProvider;
import com.example.crossborder.fx.infrastructure.FxRepository;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerCurrencyRules;
import com.example.crossborder.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class FxService implements FxQuoteService {

    private static final Duration QUOTE_TTL = Duration.ofSeconds(60);
    private static final BigDecimal BASIS_POINTS = BigDecimal.valueOf(10_000);
    private static final MathContext RATE_CONTEXT = MathContext.DECIMAL128;

    private final RateProvider rateProvider;
    private final FxRepository fxRepository;
    private final LedgerAccountService ledgerAccountService;

    public FxService(RateProvider rateProvider, FxRepository fxRepository,
                     LedgerAccountService ledgerAccountService) {
        this.rateProvider = rateProvider;
        this.fxRepository = fxRepository;
        this.ledgerAccountService = ledgerAccountService;
    }

    @Override
    @Transactional
    public FxQuote createQuote(UUID customerId, String sourceCurrency, String destinationCurrency,
                              BigDecimal sourceAmount) {
        validateCurrencyPair(customerId, sourceCurrency, destinationCurrency);
        long sourceMinor = toMinorUnits(sourceAmount, sourceCurrency);

        BigDecimal baseRate = rateProvider.getNgnToXafMidRate();
        int spreadBps = rateProvider.getSpreadBasisPoints();
        BigDecimal flatFeeAmount = rateProvider.getFlatFee(sourceCurrency);
        long flatFeeMinor = toNonnegativeMinorUnits(flatFeeAmount, sourceCurrency);
        if (sourceMinor <= flatFeeMinor) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.amount_below_fee");
        }

        BigDecimal midRate = midRate(sourceCurrency, destinationCurrency, baseRate);
        BigDecimal spreadFactor = BigDecimal.ONE.subtract(BigDecimal.valueOf(spreadBps)
                .divide(BASIS_POINTS, RATE_CONTEXT));
        BigDecimal amountAfterFee = LedgerCurrencyRules.fromMinorUnits(sourceMinor - flatFeeMinor, sourceCurrency);
        BigDecimal grossDestination = roundToCurrency(amountAfterFee.multiply(midRate), destinationCurrency);
        BigDecimal netDestination = roundToCurrency(amountAfterFee.multiply(midRate).multiply(spreadFactor),
                destinationCurrency);
        long grossDestinationMinor = toMinorUnits(grossDestination, destinationCurrency);
        long destinationMinor = toMinorUnits(netDestination, destinationCurrency);
        long spreadFeeMinor = grossDestinationMinor - destinationMinor;
        if (destinationMinor <= 0 || grossDestinationMinor < destinationMinor) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.amount_too_small");
        }

        BigDecimal effectiveRate = netDestination.divide(
                LedgerCurrencyRules.fromMinorUnits(sourceMinor, sourceCurrency), 12, RoundingMode.HALF_UP);
        UUID quoteId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(QUOTE_TTL);
        UUID rateId = fxRepository.insertRate(baseRate, spreadBps, rateProvider.providerName());
        FxQuote quote = new FxQuote(quoteId, sourceCurrency, destinationCurrency,
                LedgerCurrencyRules.fromMinorUnits(sourceMinor, sourceCurrency), netDestination,
                grossDestination, LedgerCurrencyRules.fromMinorUnits(flatFeeMinor, sourceCurrency),
                LedgerCurrencyRules.fromMinorUnits(spreadFeeMinor, destinationCurrency),
                midRate, effectiveRate, expiresAt);
        fxRepository.insertQuote(quote, customerId, sourceMinor, destinationMinor, grossDestinationMinor,
                flatFeeMinor, spreadFeeMinor, rateId);
        return quote;
    }

    @Override
    @Transactional
    public FxQuote consumeQuote(UUID customerId, UUID quoteId) {
        FxRepository.QuoteAmounts amounts = fxRepository.lockQuote(customerId, quoteId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "fx.quote_not_found"));
        if (amounts.usedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "fx.quote_used");
        }
        if (!amounts.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "fx.quote_expired");
        }
        if (!fxRepository.markQuoteUsed(quoteId)) {
            throw new ApiException(HttpStatus.CONFLICT, "fx.quote_used");
        }
        return toQuote(amounts);
    }

    @Override
    @Transactional
    public UUID getSystemAccount(String currency, String accountType) {
        validateCurrency(currency);
        if (!accountType.equals("FX_CLEARING") && !accountType.equals("SYSTEM_FEE")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.system_account_invalid");
        }
        return fxRepository.findSystemAccount(currency, accountType).orElseGet(() -> {
            UUID ledgerAccountId = ledgerAccountService.createAccount(accountType, currency);
            fxRepository.insertSystemAccount(currency, accountType, ledgerAccountId);
            return fxRepository.findSystemAccount(currency, accountType).orElse(ledgerAccountId);
        });
    }

    private FxQuote toQuote(FxRepository.QuoteAmounts amounts) {
        return new FxQuote(amounts.id(), amounts.sourceCurrency(), amounts.destinationCurrency(),
                LedgerCurrencyRules.fromMinorUnits(amounts.sourceAmountMinor(), amounts.sourceCurrency()),
                LedgerCurrencyRules.fromMinorUnits(amounts.destinationAmountMinor(), amounts.destinationCurrency()),
                LedgerCurrencyRules.fromMinorUnits(amounts.grossDestinationMinor(), amounts.destinationCurrency()),
                LedgerCurrencyRules.fromMinorUnits(amounts.flatFeeSourceMinor(), amounts.sourceCurrency()),
                LedgerCurrencyRules.fromMinorUnits(amounts.spreadFeeDestinationMinor(), amounts.destinationCurrency()),
                amounts.midRate(), amounts.effectiveRate(), amounts.expiresAt());
    }

    private BigDecimal midRate(String source, String destination, BigDecimal xafPerNgn) {
        if (source.equals("NGN") && destination.equals("XAF")) {
            return xafPerNgn;
        }
        return BigDecimal.ONE.divide(xafPerNgn, RATE_CONTEXT);
    }

    private BigDecimal roundToCurrency(BigDecimal amount, String currency) {
        return amount.setScale(currency.equals("NGN") ? 2 : 0, RoundingMode.HALF_UP);
    }

    private long toMinorUnits(BigDecimal amount, String currency) {
        try {
            return LedgerCurrencyRules.toMinorUnits(amount, currency);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.amount_invalid");
        }
    }

    private long toNonnegativeMinorUnits(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.configuration_invalid");
        }
        return amount.signum() == 0 ? 0 : toMinorUnits(amount, currency);
    }

    private void validateCurrencyPair(UUID customerId, String source, String destination) {
        if (customerId == null || source == null || destination == null || source.equals(destination)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.currency_pair_invalid");
        }
        validateCurrency(source);
        validateCurrency(destination);
    }

    private void validateCurrency(String currency) {
        if (!"NGN".equals(currency) && !"XAF".equals(currency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fx.currency_pair_invalid");
        }
    }
}