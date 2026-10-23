package com.example.crossborder.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    @Test
    void appendSerializesAndStoresAuditMetadata() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuditService service = new AuditService(jdbcTemplate, new ObjectMapper());
        UUID actorId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        service.append(actorId, "TEST_ACTION", "TEST_ENTITY", entityId, Map.of("source", "unit-test"));

        verify(jdbcTemplate).update(anyString(), any(UUID.class), eq(actorId), eq("TEST_ACTION"),
                eq("TEST_ENTITY"), eq(entityId), eq("{\"source\":\"unit-test\"}"));
    }
}