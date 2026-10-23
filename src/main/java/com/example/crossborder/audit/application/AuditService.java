package com.example.crossborder.audit.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.audit.api.AuditReader;
import com.example.crossborder.audit.api.AuditEventView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.List;

@Service
public class AuditService implements AuditWriter, AuditReader {

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

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventView> getRecentEvents(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.query("""
                        SELECT id, actor_id, action, entity_type, entity_id, metadata, created_at
                          FROM audit.events ORDER BY created_at DESC LIMIT ?
                        """,
                (resultSet, rowNumber) -> {
                    try {
                        Map<String, Object> metadata = objectMapper.readValue(
                                resultSet.getString("metadata"),
                                new com.fasterxml.jackson.core.type.TypeReference<>() { });
                        return new AuditEventView(resultSet.getObject("id", UUID.class),
                                resultSet.getObject("actor_id", UUID.class), resultSet.getString("action"),
                                resultSet.getString("entity_type"), resultSet.getObject("entity_id", UUID.class),
                                metadata, resultSet.getTimestamp("created_at").toInstant());
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Stored audit metadata is invalid", exception);
                    }
                }, boundedLimit);
    }
}