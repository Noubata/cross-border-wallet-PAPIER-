package com.example.crossborder.auth.application;

import com.example.crossborder.audit.api.AuditWriter;
import com.example.crossborder.auth.api.AuthResponse;
import com.example.crossborder.auth.domain.AuthUser;
import com.example.crossborder.auth.domain.RefreshTokenRecord;
import com.example.crossborder.auth.infrastructure.AuthRepository;
import com.example.crossborder.customer.api.CustomerRegistrationService;
import com.example.crossborder.shared.error.ApiException;
import com.example.crossborder.shared.security.AccessTokenIssuer;
import com.example.crossborder.shared.security.IssuedAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private AuthRepository authRepository;
    private CustomerRegistrationService customerRegistrationService;
    private AuditWriter auditWriter;
    private PasswordEncoder passwordEncoder;
    private AccessTokenIssuer accessTokenIssuer;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authRepository = mock(AuthRepository.class);
        customerRegistrationService = mock(CustomerRegistrationService.class);
        auditWriter = mock(AuditWriter.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accessTokenIssuer = mock(AccessTokenIssuer.class);
        authService = new AuthService(authRepository, customerRegistrationService, auditWriter,
                passwordEncoder, accessTokenIssuer, Duration.ofDays(30));
        when(accessTokenIssuer.issue(any(UUID.class), anyString()))
                .thenReturn(new IssuedAccessToken("access", Instant.now().plusSeconds(600)));
    }

    @Test
    void registerCreatesUserProfileAndSession() {
        when(authRepository.findUserByEmail("user@example.com")).thenReturn(Optional.empty());
        when(authRepository.insertUser(any(AuthUser.class))).thenReturn(true);
        when(passwordEncoder.encode("a-strong-password")).thenReturn("bcrypt-hash");

        AuthResponse response = authService.register(" User@Example.com ", "a-strong-password", "fr");

        assertEquals("user@example.com", response.email());
        assertEquals("USER", response.role());
        assertEquals("access", response.accessToken());
        verify(customerRegistrationService).createPendingProfile(response.userId(), "fr");
        verify(authRepository).insertUser(any(AuthUser.class));
        verify(authRepository).insertRefreshToken(eq(response.userId()), anyString(), any(Instant.class));
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(authRepository.findUserByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> authService.login("user@example.com", "wrong-password"));
        verify(auditWriter, never()).append(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void loginCreatesSessionForValidCredentials() {
        AuthUser user = new AuthUser(UUID.randomUUID(), "user@example.com", "hash", "USER");
        when(authRepository.findUserByEmail(user.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("a-strong-password", "hash")).thenReturn(true);

        AuthResponse response = authService.login(user.email(), "a-strong-password");

        assertEquals(user.id(), response.userId());
        verify(auditWriter).append(eq(user.id()), eq("AUTH_LOGIN"), eq("USER"), eq(user.id()), any());
    }

    @Test
    void refreshRevokesOldTokenAndIssuesReplacement() throws Exception {
        String oldToken = "refresh-token";
        String tokenHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(oldToken.getBytes(StandardCharsets.UTF_8)));
        AuthUser user = new AuthUser(UUID.randomUUID(), "user@example.com", "hash", "USER");
        when(authRepository.findActiveRefreshToken(tokenHash))
                .thenReturn(Optional.of(new RefreshTokenRecord(user.id())));
        when(authRepository.findUserById(user.id())).thenReturn(Optional.of(user));
        when(authRepository.revokeActiveRefreshToken(tokenHash, user.id())).thenReturn(true);

        AuthResponse response = authService.refresh(oldToken);

        assertEquals(user.id(), response.userId());
        verify(authRepository).insertRefreshToken(eq(user.id()), anyString(), any(Instant.class));
    }

    @Test
    void refreshRejectsReusedToken() {
        when(authRepository.findActiveRefreshToken(anyString())).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> authService.refresh("already-used"));
    }

    @Test
    void logoutRevokesTokenAndAuditsAction() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId, "refresh-token");

        verify(authRepository).revokeActiveRefreshToken(anyString(), eq(userId));
        verify(auditWriter).append(eq(userId), eq("AUTH_LOGOUT"), eq("USER"), eq(userId), any());
    }
}