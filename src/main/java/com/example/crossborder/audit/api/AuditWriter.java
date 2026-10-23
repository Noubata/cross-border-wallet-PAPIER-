package com.example.crossborder.audit.api;

import java.util.Map;
import java.util.UUID;

public interface AuditWriter {
    void append(UUID actorId, String action, String entityType, UUID entityId, Map<String, Object> metadata);
}