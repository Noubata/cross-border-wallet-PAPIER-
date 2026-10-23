package com.example.crossborder.auth.infrastructure;

import com.example.crossborder.auth.domain.AuthUser;
import com.example.crossborder.auth.domain.RefreshTokenRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AuthUser> findUserByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash, role FROM auth.users WHERE email = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new AuthUser(resultSet.getObject("id", UUID.class),
                        resultSet.getString("email"), resultSet.getString("password_hash"),
                        resultSet.getString("role")))
                        : Optional.empty(), email);
    }

    public Optional<AuthUser> findUserById(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash, role FROM auth.users WHERE id = ?
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new AuthUser(resultSet.getObject("id", UUID.class),
                        resultSet.getString("email"), resultSet.getString("password_hash"),
                        resultSet.getString("role")))
                        : Optional.empty(), userId);
    }

    public boolean insertUser(AuthUser user) {
        return jdbcTemplate.update("""
                INSERT INTO auth.users (id, email, password_hash, role) VALUES (?, ?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """, user.id(), user.email(), user.passwordHash(), user.role()) == 1;
    }

    public void insertRefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO auth.refresh_tokens (id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), userId, tokenHash, java.sql.Timestamp.from(expiresAt));
    }

    public Optional<RefreshTokenRecord> findActiveRefreshToken(String tokenHash) {
        return jdbcTemplate.query("""
                        SELECT user_id FROM auth.refresh_tokens
                         WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > now()
                        """,
                resultSet -> resultSet.next()
                        ? Optional.of(new RefreshTokenRecord(resultSet.getObject("user_id", UUID.class)))
                        : Optional.empty(), tokenHash);
    }

    public boolean revokeActiveRefreshToken(String tokenHash, UUID userId) {
        return jdbcTemplate.update("""
                UPDATE auth.refresh_tokens SET revoked_at = now()
                 WHERE token_hash = ? AND user_id = ? AND revoked_at IS NULL AND expires_at > now()
                """, tokenHash, userId) == 1;
    }
}