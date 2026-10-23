package com.example.crossborder.fx.application;

import com.example.crossborder.fx.api.FxQuote;
import com.example.crossborder.fx.api.RateProvider;
import com.example.crossborder.fx.infrastructure.FxRepository;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FxServiceTest {

    private RateProvider rateProvider;
    private FxRepository fxRepository;
    private LedgerAccountService ledgerAccountService;
    private FxService fxService;

    @BeforeEach
    void setUp() {
        rateProvider = mock(RateProvider.class);
        fxRepository = mock(FxRepository.class);
        ledgerAccountService = mock(LedgerAccountService.class);
        fxService = new FxService(rateProvider, fxRepository, ledgerAccountService);
        when(rateProvider.getNgnToXafMidRate()).thenReturn(new BigDecimal("0.42"));
        when(rateProvider.getSpreadBasisPoints()).thenReturn(150);
        when(rateProvider.getFlatFee("NGN")).thenReturn(new BigDecimal("100.00"));
        when(rateProvider.getFlatFee("XAF")).thenReturn(new BigDecimal("250"));
        when(rateProvider.providerName()).thenReturn("MOCK_FIXED");
        when(fxRepository.insertRate(any(BigDecimal.class), anyInt(), anyString())).thenReturn(UUID.randomUUID());
    }

    @Test
    void quoteNgnToXafAppliesFlatFeeAndSpreadWithXafWholeUnitRounding() {
        UUID customerId = UUID.randomUUID();

        FxQuote quote = fxService.createQuote(customerId, "NGN", "XAF", new BigDecimal("10000.00"));

        assertEquals(new BigDecimal("10000.00"), quote.sourceAmount());
        assertEquals(0, quote.flatFee().compareTo(new BigDecimal("100")));
        assertEquals(new BigDecimal("4096"), quote.destinationAmount());
        assertEquals(new BigDecimal("4158"), quote.grossDestinationAmount());
        assertEquals(new BigDecimal("62"), quote.spreadFee());
        assertTrue(quote.expiresAt().isAfter(Instant.now()));
        assertTrue(quote.expiresAt().isBefore(Instant.now().plusSeconds(61)));
        verify(fxRepository).insertQuote(eq(quote), eq(customerId), eq(1_000_000L),
            eq(4096L), eq(4158L), eq(10_000L), eq(62L), any(UUID.class));
    }

    @Test
    void quoteXafToNgnUsesInverseRate() {
        FxQuote quote = fxService.createQuote(UUID.randomUUID(), "XAF", "NGN", new BigDecimal("10000"));

        assertEquals("XAF", quote.sourceCurrency());
        assertEquals("NGN", quote.destinationCurrency());
        assertEquals(new BigDecimal("9750"), quote.sourceAmount().subtract(quote.flatFee()));
        assertEquals(new BigDecimal("23214.29"), quote.grossDestinationAmount());
        assertEquals(new BigDecimal("22866.07"), quote.destinationAmount());
        assertEquals(new BigDecimal("348.22"), quote.spreadFee());
    }

    @Test
    void quoteRejectsAmountsNotGreaterThanFlatFeeAndInvalidPairs() {
        assertThrows(ApiException.class, () -> fxService.createQuote(UUID.randomUUID(), "NGN", "XAF",
                new BigDecimal("100.00")));
        assertThrows(ApiException.class, () -> fxService.createQuote(UUID.randomUUID(), "NGN", "NGN",
                new BigDecimal("1000.00")));
        verify(fxRepository, never()).insertQuote(any(), any(), any(Long.class), any(Long.class),
                any(Long.class), any(Long.class), any(Long.class), any());
    }

    @Test
    void consumesValidQuoteOnceAndRejectsReuseOrExpiration() {
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        FxRepository.QuoteAmounts valid = new FxRepository.QuoteAmounts(quoteId, "NGN", "XAF",
                100_000, 41_000, 42_000, 10_000, 1_000, new BigDecimal("0.42"),
                new BigDecimal("0.41"), Instant.now().plusSeconds(30), null);
        when(fxRepository.lockQuote(customerId, quoteId)).thenReturn(Optional.of(valid));
        when(fxRepository.markQuoteUsed(quoteId)).thenReturn(true);

        FxQuote consumed = fxService.consumeQuote(customerId, quoteId);

        assertEquals(quoteId, consumed.id());
        verify(fxRepository).markQuoteUsed(quoteId);

        when(fxRepository.lockQuote(customerId, quoteId)).thenReturn(Optional.of(new FxRepository.QuoteAmounts(
                quoteId, "NGN", "XAF", 100_000, 41_000, 42_000, 10_000, 1_000,
                new BigDecimal("0.42"), new BigDecimal("0.41"), Instant.now().plusSeconds(30), Instant.now())));
        assertThrows(ApiException.class, () -> fxService.consumeQuote(customerId, quoteId));

        UUID expiredId = UUID.randomUUID();
        when(fxRepository.lockQuote(customerId, expiredId)).thenReturn(Optional.of(new FxRepository.QuoteAmounts(
                expiredId, "NGN", "XAF", 100_000, 41_000, 42_000, 10_000, 1_000,
                new BigDecimal("0.42"), new BigDecimal("0.41"), Instant.now().minusSeconds(1), null)));
        assertThrows(ApiException.class, () -> fxService.consumeQuote(customerId, expiredId));
    }

    @Test
    void provisionsAndReusesSystemAccountsThroughLedgerApi() {
        UUID accountId = UUID.randomUUID();
        when(fxRepository.findSystemAccount("NGN", "SYSTEM_FEE")).thenReturn(Optional.of(accountId));

        assertEquals(accountId, fxService.getSystemAccount("NGN", "SYSTEM_FEE"));
        verify(ledgerAccountService, never()).createAccount(anyString(), anyString());
    }

    @Test
    void createsMissingSystemAccountThroughLedgerPublicApi() {
        UUID accountId = UUID.randomUUID();
        when(fxRepository.findSystemAccount("XAF", "FX_CLEARING"))
                .thenReturn(Optional.empty(), Optional.of(accountId));
        when(ledgerAccountService.createAccount("FX_CLEARING", "XAF")).thenReturn(accountId);

        assertEquals(accountId, fxService.getSystemAccount("XAF", "FX_CLEARING"));
        verify(fxRepository).insertSystemAccount("XAF", "FX_CLEARING", accountId);
    }
}