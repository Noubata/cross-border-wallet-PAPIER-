package com.example.crossborder.risk.api;

import java.util.List;

public record RiskDecision(Outcome outcome, List<String> reasons) {
    public enum Outcome {
        APPROVE,
        FLAG,
        REJECT
    }
}