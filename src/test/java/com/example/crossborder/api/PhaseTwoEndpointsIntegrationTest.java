package com.example.crossborder.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.rate-limit.capacity=100")
class PhaseTwoEndpointsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerEndpointCreatesUserAndReturnsTokens() throws Exception {
        ResponseEntity<JsonNode> response = register(uniqueEmail(), "fr");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("USER", response.getBody().get("role").asText());
        assertNotNull(response.getBody().get("accessToken").asText());
        assertNotNull(response.getBody().get("refreshToken").asText());
    }

    @Test
    void loginEndpointAuthenticatesRegisteredUser() throws Exception {
        String email = uniqueEmail();
        register(email, "en");

        ResponseEntity<JsonNode> response = post("/api/auth/login",
                Map.of("email", email, "password", "strong-password-123"), JsonNode.class, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(email, response.getBody().get("email").asText());
    }

    @Test
    void refreshEndpointRotatesRefreshToken() throws Exception {
        String email = uniqueEmail();
        JsonNode registration = register(email, "en").getBody();

        ResponseEntity<JsonNode> response = post("/api/auth/refresh",
                Map.of("refreshToken", registration.get("refreshToken").asText()), JsonNode.class, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotEquals(registration.get("refreshToken").asText(),
                response.getBody().get("refreshToken").asText());
    }

    @Test
    void logoutEndpointRevokesRefreshTokenAndRequiresAccessToken() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        String accessToken = registration.get("accessToken").asText();
        String refreshToken = registration.get("refreshToken").asText();

        ResponseEntity<Void> logout = post("/api/auth/logout", Map.of("refreshToken", refreshToken),
                Void.class, accessToken);
        ResponseEntity<JsonNode> refresh = post("/api/auth/refresh", Map.of("refreshToken", refreshToken),
                JsonNode.class, null);

        assertEquals(HttpStatus.NO_CONTENT, logout.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, refresh.getStatusCode());
    }

    @Test
    void customerProfileGetEndpointReturnsOwnProfile() throws Exception {
        JsonNode registration = register(uniqueEmail(), "fr").getBody();

        ResponseEntity<JsonNode> profile = getProfile(registration.get("accessToken").asText());

        assertEquals(HttpStatus.OK, profile.getStatusCode());
        assertEquals("fr", profile.getBody().get("preferredLanguage").asText());
        assertEquals("PENDING", profile.getBody().get("kycStatus").asText());
    }

    @Test
    void customerLanguageEndpointUpdatesProfileAndAuditLog() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        HttpHeaders headers = bearerHeaders(registration.get("accessToken").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/customers/me/language", HttpMethod.PATCH,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("preferredLanguage", "fr")), headers),
                JsonNode.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("fr", response.getBody().get("preferredLanguage").asText());
    }

    @Test
    void walletEndpointsCreateListAndReadWallets() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        String accessToken = registration.get("accessToken").asText();
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<JsonNode> created = restTemplate.exchange("/api/wallets", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("currency", "NGN")), headers),
                JsonNode.class);
        ResponseEntity<JsonNode[]> listed = restTemplate.exchange("/api/wallets", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)), JsonNode[].class);
        ResponseEntity<JsonNode> fetched = restTemplate.exchange("/api/wallets/NGN", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)), JsonNode.class);

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("0.00", created.getBody().get("balance").asText());
        assertEquals(HttpStatus.OK, listed.getStatusCode());
        assertEquals(1, listed.getBody().length);
        assertEquals(HttpStatus.OK, fetched.getStatusCode());
        assertEquals(created.getBody().get("id").asText(), fetched.getBody().get("id").asText());
    }

    @Test
    void walletCreationRejectsSecondWalletForSameCurrency() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        String accessToken = registration.get("accessToken").asText();
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(Map.of("currency", "XAF")), headers);

        ResponseEntity<JsonNode> first = restTemplate.exchange("/api/wallets", HttpMethod.POST, request, JsonNode.class);
        ResponseEntity<JsonNode> second = restTemplate.exchange("/api/wallets", HttpMethod.POST, request, JsonNode.class);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    private ResponseEntity<JsonNode> register(String email, String language) throws Exception {
        return post("/api/auth/register", Map.of("email", email,
                "password", "strong-password-123", "preferredLanguage", language), JsonNode.class, null);
    }

    private ResponseEntity<JsonNode> getProfile(String accessToken) {
        return restTemplate.exchange("/api/customers/me", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(accessToken)), JsonNode.class);
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType, String accessToken)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        String json = objectMapper.writeValueAsString(body);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), responseType);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}