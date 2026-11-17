package org.example.apigateway;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient

class GatewayRouteTests {

    private static final String TEST_SECRET = generateSecret();
    private static final String WRONG_SECRET =
            "wrong-secret-wrong-secret-wrong-secret-wrong-secret-wrong-secret-0987654321";

    private static String generateSecret() {
        byte[] bytes = new byte[64];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7"))
                    .withExposedPorts(6379);

    static {
        redis.start();
    }
    private String USER_ID;

    @Autowired
    private WebTestClient webTestClient;

    @RegisterExtension
    static WireMockExtension productService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension authService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension orderService = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("PRODUCT_SERVICE_URL", productService::baseUrl);
        registry.add("AUTH_SERVICE_URL", authService::baseUrl);
        registry.add("ORDER_SERVICE_URL", orderService::baseUrl);
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    private String generateRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateValidToken(String subject) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String createTokenWithExpiration(String subject, String secret, long expiresInMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInMillis))
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void resetRateLimiter() throws Exception {
        redis.execInContainer("redis-cli", "FLUSHALL");
        USER_ID = generateRandomUUID();
    }

    @Test
    void whenMissingToken_thenReturnsUnauthorized() {
        webTestClient.get()
                .uri("/api/products/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void whenValidTokenProvided_thenProxiesToDownstreamService() {
        productService.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Test Product\",\"price\":99.99}")));

        String token = generateValidToken(USER_ID);

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Test Product");
    }
    @Test
    void whenBurstExceeded_thenReturns429() {
        stubProduct();
        String token = generateValidToken(USER_ID);

        // Concurrent on purpose: 40 sequential requests could be slow enough on a busy machine
        // for the bucket (10 tokens/s) to refill, and the test would never see a 429.
        List<Integer> statuses = parallelProductStatuses(100, i -> token, i -> null);

        assertThat(statuses).contains(200);
        assertThat(statuses).contains(429);
    }

    @Test
    void whenLoginWithoutToken_thenProxiesToAuthService() {
        authService.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"abc\"}")));

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"user\",\"password\":\"pass\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("abc");

        authService.verify(postRequestedFor(urlEqualTo("/api/auth/login")));
    }

    @Test
    void whenAuthRouteCalled_thenProductServiceIsNotHit() {
        authService.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenTokenSignedWithWrongSecret_thenReturnsUnauthorized() {
        String token = createTokenWithExpiration(USER_ID, WRONG_SECRET, 3_600_000);

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenTokenIsExpired_thenReturnsUnauthorized() {
        String token = createTokenWithExpiration(USER_ID, TEST_SECRET, -60_000);   // expired a minute ago

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenTokenIsMalformed_thenReturnsUnauthorized() {
        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer not.a.jwt")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenTokenIsTampered_thenReturnsUnauthorized() {
        String[] parts = generateValidToken(generateRandomUUID()).split("\\.");
        char c = parts[2].charAt(0);
        String tampered = parts[0] + "." + parts[1] + "."
                + (c == 'A' ? 'B' : 'A') + parts[2].substring(1);

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenCreateOrderWithoutToken_thenReturnsUnauthorized() {
        webTestClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"items\": []}")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(orderService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenValidTokenProvidedForCreateOrder_thenProxiesToOrderServiceWithUsernameHeader() {
        // 1. Stub the downstream order-service response
        orderService.stubFor(post(urlEqualTo("/api/orders"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"username\":\"" + USER_ID + "\",\"totalPrice\":1200.00}")));

        String token = generateValidToken(USER_ID);

        // 2. Send request through API Gateway with JWT
        webTestClient.post()
                .uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"items\":[{\"productId\":1,\"quantity\":1}]}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo(USER_ID)
                .jsonPath("$.totalPrice").isEqualTo(1200.00);

        // 3. Verify that the gateway forwarded the request and injected the X-Auth-Username header
        orderService.verify(postRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("X-Auth-Username", equalTo(USER_ID)));
    }

    @Test
    void whenGetOrdersCalledWithValidToken_thenProxiesAndPropagatesHeader() {
        orderService.stubFor(get(urlEqualTo("/api/orders"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":1,\"username\":\"" + USER_ID + "\",\"totalPrice\":500.00}]")));

        String token = generateValidToken(USER_ID);

        webTestClient.get()
                .uri("/api/orders")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].username").isEqualTo(USER_ID);

        orderService.verify(getRequestedFor(urlEqualTo("/api/orders"))
                .withHeader("X-Auth-Username", equalTo(USER_ID)));
    }

    @Test
    void whenDeleteOrderCalled_thenProxiesToServerAndPropagatesHeader() {
        orderService.stubFor(delete(urlEqualTo("/api/orders/5"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(204)));

        String token = generateValidToken(USER_ID);

        webTestClient.delete()
                .uri("/api/orders/5")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNoContent();

        orderService.verify(deleteRequestedFor(urlEqualTo("/api/orders/5"))
                .withHeader("X-Auth-Username", equalTo(USER_ID)));
    }

    /**
     * Fires the requests concurrently, so the outcome does not depend on how fast a single request is
     * (sequential requests on a slow machine would let the 10 tokens/s bucket refill between calls).
     * The response body is consumed, so no connection is left hanging.
     */
    private List<Integer> parallelProductStatuses(int requests,
                                                  java.util.function.IntFunction<String> token,
                                                  java.util.function.IntFunction<String> spoofedUsername) {
        return IntStream.range(0, requests).parallel()
                .mapToObj(i -> webTestClient.get()
                        .uri("/api/products/1")
                        .headers(headers -> {
                            headers.setBearerAuth(token.apply(i));
                            String spoofed = spoofedUsername.apply(i);
                            if (spoofed != null) {
                                headers.set("X-Auth-Username", spoofed);
                            }
                        })
                        .exchange()
                        .expectBody()
                        .returnResult()
                        .getStatus()
                        .value())
                .toList();
    }

    private void stubProduct() {
        productService.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    // ---------- identity header ----------

    @Test
    void whenClientSpoofsIdentityHeader_thenDownstreamOnlyReceivesTheTokenSubject() {
        stubProduct();

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + generateValidToken(USER_ID))
                .header("X-Auth-Username", "admin")
                .exchange()
                .expectStatus().isOk();

        List<String> received = productService.getAllServeEvents().getFirst()
                .getRequest().getHeaders().getHeader("X-Auth-Username").values();
        assertThat(received).containsExactly(USER_ID);
    }

    @Test
    void whenClientSpoofsIdentityHeaderOnPublicRoute_thenItIsNotForwarded() {
        authService.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));

        webTestClient.post()
                .uri("/api/auth/login")
                .header("X-Auth-Username", "admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();

        authService.verify(postRequestedFor(urlEqualTo("/api/auth/login"))
                .withoutHeader("X-Auth-Username"));
    }

    // ---------- the auth service's authenticated endpoints must be reachable through the gateway ----------

    @Test
    void whenPasswordChangeCalledWithoutToken_thenReturnsUnauthorized() {
        webTestClient.patch()
                .uri("/api/user/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(authService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenPasswordChangeCalledWithToken_thenProxiedWithAuthorizationAndIdentityHeaders() {
        authService.stubFor(patch(urlEqualTo("/api/user/password"))
                .willReturn(aResponse().withStatus(204)));
        String token = generateValidToken(USER_ID);

        webTestClient.patch()
                .uri("/api/user/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"newPassword456\"}")
                .exchange()
                .expectStatus().isNoContent();

        // the auth service validates the token itself, so Authorization must arrive untouched
        authService.verify(patchRequestedFor(urlEqualTo("/api/user/password"))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .withHeader("X-Auth-Username", equalTo(USER_ID)));
    }

    @Test
    void whenAdminEndpointsCalledWithToken_thenProxiedToAuthService() {
        authService.stubFor(put(urlEqualTo("/api/admin/users/martin/roles/admin"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
        authService.stubFor(delete(urlEqualTo("/api/admin/users/martin/roles/admin"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
        String token = generateValidToken(USER_ID);

        webTestClient.put().uri("/api/admin/users/martin/roles/admin")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk();
        webTestClient.delete().uri("/api/admin/users/martin/roles/admin")
                .header("Authorization", "Bearer " + token)
                .exchange().expectStatus().isOk();

        authService.verify(putRequestedFor(urlEqualTo("/api/admin/users/martin/roles/admin")));
        authService.verify(deleteRequestedFor(urlEqualTo("/api/admin/users/martin/roles/admin")));
    }

    @Test
    void whenAdminEndpointCalledWithoutToken_thenReturnsUnauthorized() {
        webTestClient.put()
                .uri("/api/admin/users/martin/roles/admin")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(authService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenNoRouteMatches_thenReturnsNotFoundAndNothingIsForwarded() {
        webTestClient.get()
                .uri("/api/unknown/thing")
                .header("Authorization", "Bearer " + generateValidToken(USER_ID))
                .exchange()
                .expectStatus().isNotFound();

        assertThat(authService.getAllServeEvents()).isEmpty();
        assertThat(productService.getAllServeEvents()).isEmpty();
        assertThat(orderService.getAllServeEvents()).isEmpty();
    }

    // ---------- rate limiting ----------

    @Test
    void rateLimitHeadersShouldAdvertiseTheConfiguredLimits() {
        stubProduct();

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + generateValidToken(USER_ID))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Burst-Capacity", "20")
                .expectHeader().valueEquals("X-RateLimit-Replenish-Rate", "10");
    }

    @Test
    void whenOneUserIsRateLimited_thenAnotherUserIsStillServed() {
        stubProduct();
        String heavyUser = generateValidToken(USER_ID);

        List<Integer> heavyStatuses = parallelProductStatuses(100, i -> heavyUser, i -> null);
        assertThat(heavyStatuses).contains(429);

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + generateValidToken(generateRandomUUID()))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void whenClientVariesTheSpoofedIdentityHeader_thenRateLimitStillApplies() {
        // the limiter keys on the identity header; the filter overwrites it, so spoofing cannot dodge the limit
        stubProduct();
        String token = generateValidToken(USER_ID);

        List<Integer> statuses = parallelProductStatuses(100, i -> token, i -> "spoofed-" + i);

        assertThat(statuses).contains(429);
    }

    @Test
    void loginShouldBeRateLimitedPerClientIp() {
        authService.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));

        List<Integer> statuses = IntStream.range(0, 8)
                .mapToObj(i -> webTestClient.post()
                        .uri("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"username\":\"martin\",\"password\":\"wrong\"}")
                        .exchange()
                        .expectBody()
                        .returnResult()
                        .getStatus()
                        .value())
                .toList();

        // burst of 5, then roughly one per minute
        assertThat(statuses.subList(0, 5)).containsOnly(200);
        assertThat(statuses.get(5)).isEqualTo(429);
    }

    // ---------- downstream failure ----------

    @Test
    void whenDownstreamResetsTheConnection_thenGatewayReturnsServerError() {
        productService.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + generateValidToken(USER_ID))
                .exchange()
                .expectStatus().is5xxServerError();
    }
}