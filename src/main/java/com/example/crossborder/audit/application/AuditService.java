package com.example.crossborder.audit.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService implements AuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void append(UUID actorId, String action, String entityType, UUID entityId,
                       Map<String, Object> metadata) {
        String json;
        try {
            json = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", exception);
        }

        jdbcTemplate.update("""
                INSERT INTO audit.events (id, actor_id, action, entity_type, entity_id, metadata)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, UUID.randomUUID(), actorId, action, entityType, entityId, json);
    }
}