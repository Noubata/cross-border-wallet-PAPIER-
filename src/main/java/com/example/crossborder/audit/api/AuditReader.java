package com.example.crossborder.audit.api;

import java.util.List;

public interface AuditReader {
    List<AuditEventView> getRecentEvents(int limit);
}