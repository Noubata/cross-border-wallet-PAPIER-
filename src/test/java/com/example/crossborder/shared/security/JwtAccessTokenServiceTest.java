package com.example.crossborder.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAccessTokenServiceTest {

    @Test
    void issueEncodesUserSubjectRoleAndExpiration() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        Instant now = Instant.now();
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(Jwt.withTokenValue("encoded")
                .header("alg", "HS256")
                .subject("subject")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("roles", java.util.List.of("USER"))
                .build());
        JwtAccessTokenService service = new JwtAccessTokenService(encoder, Duration.ofMinutes(10));
        UUID userId = UUID.randomUUID();

        IssuedAccessToken issued = service.issue(userId, "ADMIN");

        assertEquals("encoded", issued.value());
        verify(encoder).encode(org.mockito.ArgumentMatchers.argThat(parameters ->
                parameters.getClaims().getSubject().equals(userId.toString())
                        && parameters.getClaims().getClaim("roles").equals(java.util.List.of("ADMIN"))));
    }
}