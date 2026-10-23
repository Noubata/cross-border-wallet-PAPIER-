package com.example.crossborder.risk.application;

import com.example.crossborder.customer.api.CustomerKycQuery;
import com.example.crossborder.risk.api.RiskDecision;
import com.example.crossborder.risk.infrastructure.RiskRepository;
import com.example.crossborder.transfer.api.TransferRiskMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurableRiskEngineTest {

    private CustomerKycQuery customerKycQuery;
    private TransferRiskMetrics transferRiskMetrics;
    private RiskRepository riskRepository;
    private ConfigurableRiskEngine engine;

    @BeforeEach
    void setUp() {
        customerKycQuery = mock(CustomerKycQuery.class);
        transferRiskMetrics = mock(TransferRiskMetrics.class);
        riskRepository = mock(RiskRepository.class);
        engine = new ConfigurableRiskEngine(customerKycQuery, transferRiskMetrics, riskRepository,
                new BigDecimal("500000"), new BigDecimal("1000000"),
                new BigDecimal("100000"), new BigDecimal("250000"),
                new BigDecimal("2000000"), new BigDecimal("5000000"), 5, Duration.ofMinutes(10));
        when(customerKycQuery.getKycStatus(any(UUID.class))).thenReturn("VERIFIED");
        when(transferRiskMetrics.getDailyOutgoingTotal(any(UUID.class), any(), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(transferRiskMetrics.countRecentTransfers(any(UUID.class), any(Instant.class))).thenReturn(0L);
    }

    @Test
    void approvesBelowThresholdWhenKycAndDailyLimitAreClear() {
        UUID transferId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        RiskDecision decision = engine.evaluate(transferId, customerId, new BigDecimal("100.00"), "NGN");

        assertEquals(RiskDecision.Outcome.APPROVE, decision.outcome());
        verify(riskRepository).saveAssessment(transferId, customerId, decision);
    }

    @Test
    void flagsLargeSingleTransferAndVelocity() {
        UUID transferId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(transferRiskMetrics.countRecentTransfers(eq(customerId), any(Instant.class))).thenReturn(5L);

        RiskDecision decision = engine.evaluate(transferId, customerId, new BigDecimal("600000"), "NGN");

        assertEquals(RiskDecision.Outcome.FLAG, decision.outcome());
        assertEquals(java.util.List.of("SINGLE_TRANSFER_THRESHOLD", "TRANSFER_VELOCITY"), decision.reasons());
    }

    @Test
    void rejectsUnverifiedKycAndDailyLimitBeforeFlagRules() {
        UUID customerId = UUID.randomUUID();
        when(customerKycQuery.getKycStatus(customerId)).thenReturn("PENDING");
        when(transferRiskMetrics.getDailyOutgoingTotal(eq(customerId), eq("NGN"), any(Instant.class)))
                .thenReturn(new BigDecimal("1900000"));
        when(transferRiskMetrics.countRecentTransfers(eq(customerId), any(Instant.class))).thenReturn(5L);

        RiskDecision decision = engine.evaluate(UUID.randomUUID(), customerId,
                new BigDecimal("600000"), "NGN");

        assertEquals(RiskDecision.Outcome.REJECT, decision.outcome());
        assertEquals(java.util.List.of("KYC_UNVERIFIED_LIMIT", "DAILY_OUTGOING_LIMIT"), decision.reasons());
    }
}