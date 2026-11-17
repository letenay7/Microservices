package org.example.apigateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Which bucket does a request land in? Plain unit tests, no Spring context. */
class RateLimiterConfigTests {

    private final RateLimiterConfig config = new RateLimiterConfig();
    private final KeyResolver userKeyResolver = config.userKeyResolver();
    private final KeyResolver ipKeyResolver = config.ipKeyResolver();

    private MockServerHttpRequest.BaseBuilder<?> request() {
        return MockServerHttpRequest.get("/api/products/1")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 54321));
    }

    private String keyOf(KeyResolver resolver, MockServerHttpRequest request) {
        return resolver.resolve(MockServerWebExchange.from(request)).block();
    }

    @Test
    void userResolverShouldUseTheAuthenticatedUsername() {
        assertEquals("user:martin", keyOf(userKeyResolver, request().header("X-Auth-Username", "martin").build()));
    }

    @Test
    void userResolverShouldFallBackToTheClientIpWithoutIdentityHeader() {
        assertEquals("ip:203.0.113.9", keyOf(userKeyResolver, request().build()));
    }

    @Test
    void userResolverShouldTreatABlankIdentityHeaderAsMissing() {
        assertEquals("ip:203.0.113.9", keyOf(userKeyResolver, request().header("X-Auth-Username", "  ").build()));
    }

    @Test
    void ipResolverShouldIgnoreTheIdentityHeader() {
        assertEquals("ip:203.0.113.9", keyOf(ipKeyResolver, request().header("X-Auth-Username", "martin").build()));
    }

    @Test
    void clientSuppliedForwardedForHeaderShouldNotInfluenceTheKey() {
        // otherwise a client could pick a fresh "IP" for every request and never be limited
        MockServerHttpRequest spoofed = request().header("X-Forwarded-For", "1.2.3.4").build();

        assertEquals("ip:203.0.113.9", keyOf(ipKeyResolver, spoofed));
        assertEquals("ip:203.0.113.9", keyOf(userKeyResolver, spoofed));
    }

    @Test
    void usernameAndIpNamespacesShouldNeverCollide() {
        // a user literally named like an IP address must not share that IP's bucket
        String userNamedLikeAnIp = keyOf(userKeyResolver,
                request().header("X-Auth-Username", "203.0.113.9").build());
        String thatIp = keyOf(ipKeyResolver, request().build());

        assertNotEquals(thatIp, userNamedLikeAnIp);
    }

    @Test
    void requestWithoutRemoteAddressShouldGetAnExplicitFallbackKey() {
        MockServerHttpRequest noAddress = MockServerHttpRequest.get("/api/products/1").build();

        assertEquals("ip:unknown", keyOf(ipKeyResolver, noAddress));
    }
}