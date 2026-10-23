package com.example.crossborder.risk.infrastructure;

import com.example.crossborder.risk.api.RiskDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RiskRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RiskRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveAssessment(UUID transferId, UUID customerId, RiskDecision decision) {
        try {
            String reasons = objectMapper.writeValueAsString(decision.reasons());
            jdbcTemplate.update("""
                    INSERT INTO risk.assessments (id, transfer_id, customer_id, decision, reasons)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                    ON CONFLICT (transfer_id) DO UPDATE
                       SET decision = EXCLUDED.decision, reasons = EXCLUDED.reasons, assessed_at = now()
                    """, UUID.randomUUID(), transferId, customerId, decision.outcome().name(), reasons);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Risk assessment reasons could not be serialized", exception);
        }
    }
}