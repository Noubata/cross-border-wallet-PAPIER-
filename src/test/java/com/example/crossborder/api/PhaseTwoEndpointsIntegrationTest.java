package com.example.crossborder.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.crossborder.wallet.api.WalletView;
import com.example.crossborder.funding.infrastructure.FakePaymentGateway;
import com.example.crossborder.ledger.api.LedgerBalanceService;
import com.example.crossborder.customer.api.CustomerRegistrationService;
import com.example.crossborder.ledger.api.LedgerAccountService;
import com.example.crossborder.ledger.api.LedgerEntryRequest;
import com.example.crossborder.ledger.api.LedgerPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "auth.rate-limit.capacity=100")
class PhaseTwoEndpointsIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("wallet")
            .withUsername("wallet")
            .withPassword("wallet");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("TEST_DATABASE_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
            return;
        }
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgresContainer() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRegistrationService customerRegistrationService;

    @Autowired
    private LedgerAccountService ledgerAccountService;

    @Autowired
    private LedgerPostingService ledgerPostingService;

    @Autowired
    private LedgerBalanceService ledgerBalanceService;

    @Autowired
    private FakePaymentGateway fakePaymentGateway;

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
        assertEquals(0, new java.math.BigDecimal(created.getBody().get("balance").asText())
            .compareTo(java.math.BigDecimal.ZERO));
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

        @Test
        void transferEndpointReplaysOriginalResponseAndConflictsOnDifferentPayload() throws Exception {
        JsonNode sourceRegistration = register(uniqueEmail(), "en").getBody();
        JsonNode destinationRegistration = register(uniqueEmail(), "en").getBody();
        String accessToken = sourceRegistration.get("accessToken").asText();
        WalletView source = createWallet(accessToken, "NGN");
        WalletView destination = createWallet(destinationRegistration.get("accessToken").asText(), "NGN");
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "phase-four-idempotency-" + UUID.randomUUID());
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "sourceWalletId", source.id(), "destinationWalletId", destination.id(), "amount", "1.00"));

        ResponseEntity<JsonNode> first = restTemplate.exchange("/api/transfers", HttpMethod.POST,
            new HttpEntity<>(requestBody, headers), JsonNode.class);
        ResponseEntity<JsonNode> replay = restTemplate.exchange("/api/transfers", HttpMethod.POST,
            new HttpEntity<>(requestBody, headers), JsonNode.class);
        String changedBody = objectMapper.writeValueAsString(Map.of(
            "sourceWalletId", source.id(), "destinationWalletId", destination.id(), "amount", "2.00"));
        ResponseEntity<JsonNode> conflict = restTemplate.exchange("/api/transfers", HttpMethod.POST,
            new HttpEntity<>(changedBody, headers), JsonNode.class);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, first.getStatusCode());
        assertEquals(first.getBody(), replay.getBody());
        assertEquals(first.getStatusCode(), replay.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        }

        private WalletView createWallet(String accessToken, String currency) throws Exception {
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<WalletView> response = restTemplate.exchange("/api/wallets", HttpMethod.POST,
            new HttpEntity<>(objectMapper.writeValueAsString(Map.of("currency", currency)), headers),
            WalletView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        return response.getBody();
        }

    private String registerAdminAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email, "en");
        jdbcTemplate.update("UPDATE auth.users SET role = 'ADMIN' WHERE email = ?", email);
        ResponseEntity<JsonNode> login = post("/api/auth/login",
                Map.of("email", email, "password", "strong-password-123"), JsonNode.class, null);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        return login.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> createFlaggedTransfer(String accessToken, WalletView source,
                                                            WalletView destination, String idempotencyKey)
            throws Exception {
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        String body = objectMapper.writeValueAsString(Map.of(
                "sourceWalletId", source.id(), "destinationWalletId", destination.id(), "amount", "600000.00"));
        return restTemplate.exchange("/api/transfers", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private UUID ledgerAccountId(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT ledger_account_id FROM wallet.wallets WHERE id = ?",
                UUID.class, walletId);
    }

    private ResponseEntity<JsonNode> register(String email, String language) throws Exception {
        return post("/api/auth/register", Map.of("email", email,
                "password", "strong-password-123", "preferredLanguage", language), JsonNode.class, null);
    }

        @Test
        void fxQuoteEndpointReturnsShortLivedQuoteWithFees() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        HttpHeaders headers = bearerHeaders(registration.get("accessToken").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "sourceCurrency", "NGN", "destinationCurrency", "XAF", "sourceAmount", "10000.00"));

        ResponseEntity<JsonNode> response = restTemplate.exchange("/api/fx/quotes", HttpMethod.POST,
            new HttpEntity<>(requestBody, headers), JsonNode.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("NGN", response.getBody().get("sourceCurrency").asText());
        assertEquals("XAF", response.getBody().get("destinationCurrency").asText());
        assertEquals(0, new java.math.BigDecimal(response.getBody().get("destinationAmount").asText())
            .compareTo(new java.math.BigDecimal("4096")));
        assertEquals(0, new java.math.BigDecimal(response.getBody().get("flatFee").asText())
            .compareTo(new java.math.BigDecimal("100")));
        }

        @Test
        void conversionEndpointConsumesQuoteAndReplaysOriginalInsufficientFundsResponse() throws Exception {
        JsonNode sourceRegistration = register(uniqueEmail(), "en").getBody();
        JsonNode destinationRegistration = register(uniqueEmail(), "en").getBody();
        String sourceToken = sourceRegistration.get("accessToken").asText();
        WalletView sourceWallet = createWallet(sourceToken, "NGN");
        WalletView destinationWallet = createWallet(destinationRegistration.get("accessToken").asText(), "XAF");

        HttpHeaders quoteHeaders = bearerHeaders(sourceToken);
        quoteHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> quoteResponse = restTemplate.exchange("/api/fx/quotes", HttpMethod.POST,
            new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                "sourceCurrency", "NGN", "destinationCurrency", "XAF", "sourceAmount", "1000.00")),
                quoteHeaders), JsonNode.class);
        String quoteId = quoteResponse.getBody().get("id").asText();

        HttpHeaders convertHeaders = bearerHeaders(sourceToken);
        convertHeaders.setContentType(MediaType.APPLICATION_JSON);
        convertHeaders.set("Idempotency-Key", "fx-convert-idempotency-" + UUID.randomUUID());
        String conversionRequest = objectMapper.writeValueAsString(Map.of(
            "sourceWalletId", sourceWallet.id(), "destinationWalletId", destinationWallet.id(),
            "quoteId", quoteId));
        ResponseEntity<JsonNode> first = restTemplate.exchange("/api/transfers/convert", HttpMethod.POST,
            new HttpEntity<>(conversionRequest, convertHeaders), JsonNode.class);
        ResponseEntity<JsonNode> replay = restTemplate.exchange("/api/transfers/convert", HttpMethod.POST,
            new HttpEntity<>(conversionRequest, convertHeaders), JsonNode.class);

        assertEquals(HttpStatus.CREATED, quoteResponse.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, first.getStatusCode());
        assertEquals(first.getStatusCode(), replay.getStatusCode());
        assertEquals(first.getBody(), replay.getBody());
        assertEquals("FAILED", first.getBody().get("state").asText());
        }

        @Test
        void adminRoutesRequireAdminAndAllowWalletAndAuditOperations() throws Exception {
        JsonNode user = register(uniqueEmail(), "en").getBody();
        ResponseEntity<JsonNode> denied = restTemplate.exchange("/api/admin/audit", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders(user.get("accessToken").asText())), JsonNode.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());

        String adminToken = registerAdminAndLogin();
        WalletView wallet = createWallet(adminToken, "NGN");
        HttpHeaders adminHeaders = bearerHeaders(adminToken);
        ResponseEntity<Void> freeze = restTemplate.exchange("/api/admin/wallets/" + wallet.id() + "/freeze",
            HttpMethod.POST, new HttpEntity<>(adminHeaders), Void.class);
        ResponseEntity<Void> unfreeze = restTemplate.exchange("/api/admin/wallets/" + wallet.id() + "/unfreeze",
            HttpMethod.POST, new HttpEntity<>(adminHeaders), Void.class);
        ResponseEntity<JsonNode[]> audit = restTemplate.exchange("/api/admin/audit?limit=50", HttpMethod.GET,
            new HttpEntity<>(adminHeaders), JsonNode[].class);

        assertEquals(HttpStatus.NO_CONTENT, freeze.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, unfreeze.getStatusCode());
        assertEquals(HttpStatus.OK, audit.getStatusCode());
        assertTrue(java.util.Arrays.stream(audit.getBody())
            .anyMatch(event -> event.get("action").asText().equals("WALLET_FROZEN")));
        }

        @Test
        void adminCanApproveAndRejectFlaggedTransfers() throws Exception {
        String adminToken = registerAdminAndLogin();
        JsonNode sender = register(uniqueEmail(), "en").getBody();
        UUID senderId = UUID.fromString(sender.get("userId").asText());
        String senderToken = sender.get("accessToken").asText();
        WalletView source = createWallet(senderToken, "NGN");
        JsonNode firstRecipient = register(uniqueEmail(), "en").getBody();
        WalletView firstDestination = createWallet(firstRecipient.get("accessToken").asText(), "NGN");
        JsonNode secondRecipient = register(uniqueEmail(), "en").getBody();
        WalletView secondDestination = createWallet(secondRecipient.get("accessToken").asText(), "NGN");
        jdbcTemplate.update("UPDATE customer.profiles SET kyc_status = 'VERIFIED' WHERE customer_id = ?", senderId);
        UUID sourceAccount = ledgerAccountId(source.id());
        UUID fundingAccount = ledgerAccountService.createAccount("TEST_FUNDING", "NGN");
        ledgerPostingService.postTransaction(UUID.randomUUID(), "admin endpoint funding", java.util.List.of(
            new LedgerEntryRequest(fundingAccount, new java.math.BigDecimal("1200000.00"), java.math.BigDecimal.ZERO),
            new LedgerEntryRequest(sourceAccount, java.math.BigDecimal.ZERO, new java.math.BigDecimal("1200000.00"))));

        ResponseEntity<JsonNode> flaggedForApproval = createFlaggedTransfer(senderToken, source,
            firstDestination, "approve-" + UUID.randomUUID());
        ResponseEntity<JsonNode> flaggedForRejection = createFlaggedTransfer(senderToken, source,
            secondDestination, "reject-" + UUID.randomUUID());
        HttpHeaders adminHeaders = bearerHeaders(adminToken);
        ResponseEntity<JsonNode[]> queue = restTemplate.exchange("/api/admin/transfers/flagged", HttpMethod.GET,
            new HttpEntity<>(adminHeaders), JsonNode[].class);
        ResponseEntity<JsonNode> approved = restTemplate.exchange("/api/admin/transfers/"
                + flaggedForApproval.getBody().get("id").asText() + "/approve", HttpMethod.POST,
            new HttpEntity<>(adminHeaders), JsonNode.class);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> rejected = restTemplate.exchange("/api/admin/transfers/"
                + flaggedForRejection.getBody().get("id").asText() + "/reject", HttpMethod.POST,
            new HttpEntity<>(objectMapper.writeValueAsString(Map.of("reason", "manual review")), adminHeaders),
            JsonNode.class);

        assertEquals(HttpStatus.ACCEPTED, flaggedForApproval.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, flaggedForRejection.getStatusCode());
        assertTrue(queue.getBody().length >= 2);
        assertEquals("COMPLETED", approved.getBody().get("state").asText());
        assertEquals("FAILED", rejected.getBody().get("state").asText());
        }

        @Test
        void fundingWebhookVerifiesSignatureAndCreditsWalletExactlyOnce() throws Exception {
        JsonNode registration = register(uniqueEmail(), "en").getBody();
        String accessToken = registration.get("accessToken").asText();
        WalletView wallet = createWallet(accessToken, "NGN");
        HttpHeaders headers = bearerHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "walletId", wallet.id(), "amount", "123.45", "currency", "NGN", "email", uniqueEmail()));

        ResponseEntity<JsonNode> payment = restTemplate.exchange("/api/funding/payments", HttpMethod.POST,
            new HttpEntity<>(requestBody, headers), JsonNode.class);
        String reference = payment.getBody().get("reference").asText();
        UUID ledgerAccountId = ledgerAccountId(wallet.id());
        String webhookBody = objectMapper.writeValueAsString(Map.of("event", "charge.success", "data", Map.of(
            "reference", reference, "amount", 12345, "currency", "NGN")));

        ResponseEntity<JsonNode> invalid = restTemplate.exchange("/api/funding/webhooks/paystack", HttpMethod.POST,
            new HttpEntity<>(webhookBody, webhookHeaders("invalid")), JsonNode.class);
        String signature = fakePaymentGateway.signWebhook(webhookBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<JsonNode> first = restTemplate.exchange("/api/funding/webhooks/paystack", HttpMethod.POST,
            new HttpEntity<>(webhookBody, webhookHeaders(signature)), JsonNode.class);
        ResponseEntity<JsonNode> duplicate = restTemplate.exchange("/api/funding/webhooks/paystack", HttpMethod.POST,
            new HttpEntity<>(webhookBody, webhookHeaders(signature)), JsonNode.class);

        assertEquals(HttpStatus.CREATED, payment.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getStatusCode());
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, duplicate.getStatusCode());
        assertEquals(new java.math.BigDecimal("123.45"), ledgerBalanceService.getBalance(ledgerAccountId));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM funding.payments WHERE gateway_reference = ? AND status = 'SUCCEEDED'",
            Integer.class, reference));
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

    private HttpHeaders webhookHeaders(String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-paystack-signature", signature);
        return headers;
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}