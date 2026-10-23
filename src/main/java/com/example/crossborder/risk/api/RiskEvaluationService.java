package com.example.crossborder.risk.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface RiskEvaluationService {
    RiskDecision evaluate(UUID transferId, UUID customerId, BigDecimal outgoingAmount, String currency);
}