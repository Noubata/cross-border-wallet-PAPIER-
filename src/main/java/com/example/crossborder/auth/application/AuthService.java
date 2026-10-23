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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthRepository authRepository;
    private final CustomerRegistrationService customerRegistrationService;
    private final AuditWriter auditWriter;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final Duration refreshTokenTtl;

    public AuthService(AuthRepository authRepository,
                       CustomerRegistrationService customerRegistrationService,
                       AuditWriter auditWriter,
                       PasswordEncoder passwordEncoder,
                       AccessTokenIssuer accessTokenIssuer,
                       @Value("${security.jwt.refresh-token-ttl:P30D}") Duration refreshTokenTtl) {
        this.authRepository = authRepository;
        this.customerRegistrationService = customerRegistrationService;
        this.auditWriter = auditWriter;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public AuthResponse register(String email, String password, String preferredLanguage) {
        String normalizedEmail = normalizeEmail(email);
        if (authRepository.findUserByEmail(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "auth.email_exists");
        }

        AuthUser user = new AuthUser(UUID.randomUUID(), normalizedEmail,
                passwordEncoder.encode(password), "USER");
        if (!authRepository.insertUser(user)) {
            throw new ApiException(HttpStatus.CONFLICT, "auth.email_exists");
        }

        customerRegistrationService.createPendingProfile(user.id(), preferredLanguage);
        auditWriter.append(user.id(), "AUTH_REGISTERED", "USER", user.id(), Map.of("role", user.role()));
        return createSession(user);
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        AuthUser user = authRepository.findUserByEmail(normalizeEmail(email))
                .filter(candidate -> passwordEncoder.matches(password, candidate.passwordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.credentials_invalid"));
        auditWriter.append(user.id(), "AUTH_LOGIN", "USER", user.id(), Map.of());
        return createSession(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        String tokenHash = hashToken(refreshToken);
        RefreshTokenRecord tokenRecord = authRepository.findActiveRefreshToken(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid"));
        AuthUser user = authRepository.findUserById(tokenRecord.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid"));
        if (!authRepository.revokeActiveRefreshToken(tokenHash, user.id())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid");
        }
        auditWriter.append(user.id(), "AUTH_REFRESHED", "USER", user.id(), Map.of());
        return createSession(user);
    }

    @Transactional
    public void logout(UUID userId, String refreshToken) {
        authRepository.revokeActiveRefreshToken(hashToken(refreshToken), userId);
        auditWriter.append(userId, "AUTH_LOGOUT", "USER", userId, Map.of());
    }

    private AuthResponse createSession(AuthUser user) {
        IssuedAccessToken accessToken = accessTokenIssuer.issue(user.id(), user.role());
        String refreshToken = newRefreshToken();
        Instant refreshExpiresAt = Instant.now().plus(refreshTokenTtl);
        authRepository.insertRefreshToken(user.id(), hashToken(refreshToken), refreshExpiresAt);
        return new AuthResponse(user.id(), user.email(), user.role(), accessToken.value(), refreshToken,
                Duration.between(Instant.now(), accessToken.expiresAt()).toSeconds());
    }

    private String newRefreshToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}