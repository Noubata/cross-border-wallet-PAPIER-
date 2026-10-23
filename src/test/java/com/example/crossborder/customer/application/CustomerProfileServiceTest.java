package com.example.crossborder.customer.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.customer.api.CustomerProfileView;
import com.example.crossborder.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerProfileServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AuditWriter auditWriter;
    private CustomerProfileService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        auditWriter = mock(AuditWriter.class);
        service = new CustomerProfileService(jdbcTemplate, auditWriter);
    }

    @Test
    void createsPendingProfileWithNormalizedLanguage() {
        UUID customerId = UUID.randomUUID();

        service.createPendingProfile(customerId, "FR");

        verify(jdbcTemplate).update(anyString(), any(UUID.class), eq(customerId), eq("fr"));
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThrows(ApiException.class, () -> service.createPendingProfile(UUID.randomUUID(), "es"));
    }

    @Test
    void getsExistingProfile() {
        UUID customerId = UUID.randomUUID();
        CustomerProfileView profile = new CustomerProfileView(customerId, "en", "PENDING");
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(customerId))).thenReturn(profile);

        assertEquals(profile, service.getProfile(customerId));
    }

    @Test
    void updatesLanguageAndWritesAuditEvent() {
        UUID customerId = UUID.randomUUID();
        CustomerProfileView profile = new CustomerProfileView(customerId, "fr", "PENDING");
        when(jdbcTemplate.update(anyString(), eq("fr"), eq(customerId))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(customerId))).thenReturn(profile);

        assertEquals(profile, service.updatePreferredLanguage(customerId, "fr"));
        verify(auditWriter).append(eq(customerId), eq("CUSTOMER_LANGUAGE_UPDATED"), eq("CUSTOMER"),
                eq(customerId), any());
    }

    @Test
    void updateFailsWhenProfileDoesNotExist() {
        when(jdbcTemplate.update(anyString(), eq("fr"), any(UUID.class))).thenReturn(0);

        assertThrows(ApiException.class, () -> service.updatePreferredLanguage(UUID.randomUUID(), "fr"));
    }
}