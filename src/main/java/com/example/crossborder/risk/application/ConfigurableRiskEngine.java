package com.example.crossborder.risk.application;

import com.example.crossborder.customer.api.CustomerKycQuery;
import com.example.crossborder.risk.api.RiskDecision;
import com.example.crossborder.risk.api.RiskEvaluationService;
import com.example.crossborder.risk.infrastructure.RiskRepository;
import com.example.crossborder.transfer.api.TransferRiskMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigurableRiskEngine implements RiskEvaluationService {

    private final CustomerKycQuery customerKycQuery;
    private final TransferRiskMetrics transferRiskMetrics;
    private final RiskRepository riskRepository;
    private final Map<String, BigDecimal> singleTransferThresholds;
    private final Map<String, BigDecimal> kycLimits;
    private final Map<String, BigDecimal> dailyLimits;
    private final int maxTransfers;
    private final Duration velocityWindow;

    public ConfigurableRiskEngine(CustomerKycQuery customerKycQuery,
                                  TransferRiskMetrics transferRiskMetrics,
                                  RiskRepository riskRepository,
                                  @Value("${risk.single-transfer-threshold.NGN:500000.00}") BigDecimal ngnThreshold,
                                  @Value("${risk.single-transfer-threshold.XAF:1000000}") BigDecimal xafThreshold,
                                  @Value("${risk.kyc-unverified-limit.NGN:100000.00}") BigDecimal ngnKycLimit,
                                  @Value("${risk.kyc-unverified-limit.XAF:250000}") BigDecimal xafKycLimit,
                                  @Value("${risk.daily-outgoing-limit.NGN:2000000.00}") BigDecimal ngnDailyLimit,
                                  @Value("${risk.daily-outgoing-limit.XAF:5000000}") BigDecimal xafDailyLimit,
                                  @Value("${risk.velocity.max-transfers:5}") int maxTransfers,
                                  @Value("${risk.velocity.window:PT10M}") Duration velocityWindow) {
        if (maxTransfers < 1 || velocityWindow.isNegative() || velocityWindow.isZero()) {
            throw new IllegalArgumentException("Risk velocity settings must be positive");
        }
        this.customerKycQuery = customerKycQuery;
        this.transferRiskMetrics = transferRiskMetrics;
        this.riskRepository = riskRepository;
        this.singleTransferThresholds = Map.of("NGN", ngnThreshold, "XAF", xafThreshold);
        this.kycLimits = Map.of("NGN", ngnKycLimit, "XAF", xafKycLimit);
        this.dailyLimits = Map.of("NGN", ngnDailyLimit, "XAF", xafDailyLimit);
        this.maxTransfers = maxTransfers;
        this.velocityWindow = velocityWindow;
    }

    @Override
    @Transactional
    public RiskDecision evaluate(UUID transferId, UUID customerId, BigDecimal outgoingAmount, String currency) {
        BigDecimal threshold = requireRule(singleTransferThresholds, currency);
        BigDecimal kycLimit = requireRule(kycLimits, currency);
        BigDecimal dailyLimit = requireRule(dailyLimits, currency);
        List<String> rejected = new ArrayList<>();
        List<String> flagged = new ArrayList<>();

        String kycStatus = customerKycQuery.getKycStatus(customerId);
        if (!"VERIFIED".equals(kycStatus) && outgoingAmount.compareTo(kycLimit) > 0) {
            rejected.add("KYC_UNVERIFIED_LIMIT");
        }

        BigDecimal todayOutgoing = transferRiskMetrics.getDailyOutgoingTotal(customerId, currency,
                LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC));
        if (todayOutgoing.add(outgoingAmount).compareTo(dailyLimit) > 0) {
            rejected.add("DAILY_OUTGOING_LIMIT");
        }

        if (outgoingAmount.compareTo(threshold) > 0) {
            flagged.add("SINGLE_TRANSFER_THRESHOLD");
        }
        if (transferRiskMetrics.countRecentTransfers(customerId, java.time.Instant.now().minus(velocityWindow))
                >= maxTransfers) {
            flagged.add("TRANSFER_VELOCITY");
        }

        RiskDecision decision = !rejected.isEmpty()
                ? new RiskDecision(RiskDecision.Outcome.REJECT, List.copyOf(rejected))
                : !flagged.isEmpty()
                ? new RiskDecision(RiskDecision.Outcome.FLAG, List.copyOf(flagged))
                : new RiskDecision(RiskDecision.Outcome.APPROVE, List.of());
        riskRepository.saveAssessment(transferId, customerId, decision);
        return decision;
    }

    private BigDecimal requireRule(Map<String, BigDecimal> rules, String currency) {
        BigDecimal amount = rules.get(currency);
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Risk rule is not configured for currency " + currency);
        }
        return amount;
    }
}