package com.example.crossborder.admin.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordTransferReview(UUID transferId, UUID adminId, String decision, String reason) {
        jdbcTemplate.update("""
                INSERT INTO admin.transfer_reviews (id, transfer_id, admin_id, decision, reason)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), transferId, adminId, decision,
                reason == null || reason.isBlank() ? null : reason.trim());
    }
}