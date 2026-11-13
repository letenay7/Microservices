package org.example.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests of the JWT filter: no Spring context and no Docker, so they run in milliseconds.
 * "Forwarded" means the filter handed the request on to the downstream chain.
 */
class JwtGatewayFilterTests {

    private static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret-1234567890";
    private static final String OTHER_SECRET = "another-secret-another-secret-another-secret-0987654321-xx";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private final JwtGatewayFilter filter = new JwtGatewayFilter(SECRET);
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    // ---------- helpers ----------

    private ServerWebExchange exchange(String uri, String... headers) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.method(HttpMethod.GET, URI.create(uri));
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private void run(ServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
    }

    private String token(String subject, SecretKey key, Date expiry) {
        var builder = Jwts.builder().issuedAt(new Date()).expiration(expiry);
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.signWith(key).compact();
    }

    private String validToken(String subject) {
        return token(subject, KEY, new Date(System.currentTimeMillis() + 3_600_000));
    }

    private void assertRejected(ServerWebExchange exchange) {
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        assertNull(forwarded.get(), "a rejected request must never reach the downstream service");
    }

    // ---------- valid tokens ----------

    @Test
    void validTokenShouldBeForwardedWithTheSubjectAsIdentityHeader() {
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + validToken("martin"));

        run(exchange);

        assertNotNull(forwarded.get(), "request must be forwarded");
        assertEquals("martin", forwarded.get().getRequest().getHeaders().getFirst("X-Auth-Username"));
    }

    @Test
    void authorizationHeaderShouldBeKeptForTheDownstreamService() {
        // the auth service validates the token itself, so it has to receive it unchanged
        String bearer = "Bearer " + validToken("martin");
        ServerWebExchange exchange = exchange("/api/user/password", "Authorization", bearer);

        run(exchange);

        assertEquals(bearer, forwarded.get().getRequest().getHeaders().getFirst("Authorization"));
    }

    // ---------- rejected tokens ----------

    @Test
    void missingAuthorizationHeaderShouldBeRejected() {
        ServerWebExchange exchange = exchange("/api/products/1");

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void nonBearerSchemeShouldBeRejected() {
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Basic dXNlcjpwYXNz");

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void emptyBearerTokenShouldBeRejected() {
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer ");

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void malformedTokenShouldBeRejected() {
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer not.a.jwt");

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void tokenSignedWithAnotherSecretShouldBeRejected() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String forged = token("martin", otherKey, new Date(System.currentTimeMillis() + 3_600_000));
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + forged);

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void expiredTokenShouldBeRejected() {
        String expired = token("martin", KEY, new Date(System.currentTimeMillis() - 60_000));
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + expired);

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void tamperedSignatureShouldBeRejected() {
        String[] parts = validToken("martin").split("\\.");
        char first = parts[2].charAt(0);
        String tampered = parts[0] + "." + parts[1] + "." + (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + tampered);

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void unsignedTokenShouldBeRejected() {
        // the classic "alg: none" attack: a token that claims to need no signature
        String unsigned = Jwts.builder()
                .subject("admin")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .compact();
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + unsigned);

        run(exchange);

        assertRejected(exchange);
    }

    @Test
    void correctlySignedTokenWithoutSubjectShouldBeRejected() {
        String noSubject = token(null, KEY, new Date(System.currentTimeMillis() + 3_600_000));
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + noSubject);

        run(exchange);

        assertRejected(exchange);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void correctlySignedTokenWithBlankSubjectShouldBeRejected(String blankSubject) {
        String token = token(blankSubject, KEY, new Date(System.currentTimeMillis() + 3_600_000));
        ServerWebExchange exchange = exchange("/api/products/1", "Authorization", "Bearer " + token);

        run(exchange);

        assertRejected(exchange);
    }

    // ---------- identity header spoofing ----------

    @Test
    void spoofedIdentityHeaderShouldBeReplacedByTheTokenSubject() {
        ServerWebExchange exchange = exchange("/api/products/1",
                "Authorization", "Bearer " + validToken("martin"),
                "X-Auth-Username", "admin");

        run(exchange);

        List<String> received = forwarded.get().getRequest().getHeaders().get("X-Auth-Username");
        assertEquals(List.of("martin"), received, "downstream must see exactly one value: the token subject");
    }

    @Test
    void spoofedIdentityHeaderShouldBeReplacedWhateverItsCase() {
        ServerWebExchange exchange = exchange("/api/products/1",
                "Authorization", "Bearer " + validToken("martin"),
                "x-auth-username", "admin");

        run(exchange);

        List<String> received = forwarded.get().getRequest().getHeaders().get("X-Auth-Username");
        assertEquals(List.of("martin"), received);
    }

    @Test
    void spoofedIdentityHeaderShouldBeRemovedOnPublicRoutes() {
        ServerWebExchange exchange = exchange("/api/auth/login", "X-Auth-Username", "admin");

        run(exchange);

        assertNotNull(forwarded.get(), "public route must be forwarded");
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Auth-Username"));
    }

    // ---------- which paths are public ----------

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/register", "/api/auth/refresh-token", "/api/auth/logout"})
    void credentialEndpointsShouldBeForwardedWithoutToken(String path) {
        run(exchange(path));

        assertNotNull(forwarded.get(), path + " must be reachable without a token");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth",
            "/api/auth/",
            "/api/authx/login",
            "/api/auth/login/extra",
            "/api/auth/something-new",
            "/api/products/1?next=/api/auth/login"})
    void lookalikePublicPathsShouldStillRequireAToken(String uri) {
        ServerWebExchange exchange = exchange(uri);

        run(exchange);

        assertRejected(exchange);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/../products/1",
            "/api/auth/%2e%2e/products/1",
            "/api/auth/login/../../orders"})
    void dotSegmentsMustNotSmuggleARequestOutOfThePublicPrefix(String uri) {
        ServerWebExchange exchange = exchange(uri);

        run(exchange);

        assertRejected(exchange);
    }

    // ---------- startup ----------

    @Test
    void secretShorterThan32BytesShouldFailAtStartup() {
        assertThrows(WeakKeyException.class, () -> new JwtGatewayFilter("too-short"));
    }
}