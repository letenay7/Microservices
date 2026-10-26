package org.example.apigateway;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient

class GatewayRouteTests {

    private static final String TEST_SECRET = generateSecret();
    private static final String WRONG_SECRET =
            "wrong-secret-wrong-secret-wrong-secret-wrong-secret-wrong-secret-0987654321";
    private static final String TEST_USER = "testUser";

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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("PRODUCT_SERVICE_URL", productService::baseUrl);
        registry.add("AUTH_SERVICE_URL", authService::baseUrl);
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    private String generateValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(TEST_USER)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String createTokenWithExpiration(String secret, long expiresInMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(TEST_USER)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiresInMillis))
                .signWith(key)
                .compact();
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

        String token = generateValidToken();

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
        productService.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        String token = generateValidToken();

        List<Integer> statuses = IntStream.range(0, 40)
                .mapToObj(i -> webTestClient.get()
                        .uri("/api/products/1")
                        .header("Authorization", "Bearer " + token)
                        .exchange()
                        .returnResult(String.class)
                        .getStatus()
                        .value())
                .toList();

        assertThat(statuses.getFirst()).isEqualTo(200);
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
        String token = createTokenWithExpiration(WRONG_SECRET, 3_600_000);

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }

    @Test
    void whenTokenIsExpired_thenReturnsUnauthorized() {
        String token = createTokenWithExpiration(TEST_SECRET, -60_000);   // expired a minute ago

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
        String token = generateValidToken();
        // flip the last character of the signature
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        webTestClient.get()
                .uri("/api/products/1")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(productService.getAllServeEvents()).isEmpty();
    }
}