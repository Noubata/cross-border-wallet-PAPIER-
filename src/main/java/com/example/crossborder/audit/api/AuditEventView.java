package com.example.crossborder.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(UUID id, UUID actorId, String action, String entityType,
                             UUID entityId, Map<String, Object> metadata, Instant createdAt) {
}