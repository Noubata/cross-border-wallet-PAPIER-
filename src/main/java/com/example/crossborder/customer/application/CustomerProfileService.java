package com.example.crossborder.customer.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.customer.api.CustomerProfileView;
import com.example.crossborder.customer.api.CustomerRegistrationService;
import com.example.crossborder.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CustomerProfileService implements CustomerRegistrationService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditWriter auditWriter;

    public CustomerProfileService(JdbcTemplate jdbcTemplate, AuditWriter auditWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public void createPendingProfile(UUID customerId, String preferredLanguage) {
        String language = normalizeLanguage(preferredLanguage);
        jdbcTemplate.update("""
                INSERT INTO customer.profiles (id, customer_id, preferred_language, kyc_status)
                VALUES (?, ?, ?, 'PENDING')
                """, UUID.randomUUID(), customerId, language);
    }

    @Transactional(readOnly = true)
    public CustomerProfileView getProfile(UUID customerId) {
        CustomerProfileView profile = jdbcTemplate.query("""
                        SELECT customer_id, preferred_language, kyc_status
                          FROM customer.profiles WHERE customer_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? new CustomerProfileView(resultSet.getObject("customer_id", UUID.class),
                        resultSet.getString("preferred_language"), resultSet.getString("kyc_status"))
                        : null,
                customerId);
        if (profile == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "customer.not_found");
        }
        return profile;
    }

    @Transactional
    public CustomerProfileView updatePreferredLanguage(UUID customerId, String preferredLanguage) {
        String language = normalizeLanguage(preferredLanguage);
        int updated = jdbcTemplate.update("""
                UPDATE customer.profiles
                   SET preferred_language = ?, updated_at = now()
                 WHERE customer_id = ?
                """, language, customerId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "customer.not_found");
        }
        auditWriter.append(customerId, "CUSTOMER_LANGUAGE_UPDATED", "CUSTOMER", customerId,
                Map.of("preferredLanguage", language));
        return getProfile(customerId);
    }

    private String normalizeLanguage(String language) {
        if (language == null) {
            return "en";
        }
        String normalized = language.toLowerCase();
        if (!normalized.equals("en") && !normalized.equals("fr")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "customer.language_invalid");
        }
        return normalized;
    }
}