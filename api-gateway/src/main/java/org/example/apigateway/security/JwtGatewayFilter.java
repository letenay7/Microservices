package org.example.apigateway.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(JwtGatewayFilter.class);

    static final String USERNAME_HEADER = "X-Auth-Username";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh-token",
            "/api/auth/logout");

    private final SecretKey key;

    public JwtGatewayFilter(@Value("${jwt.secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Never trust a client-supplied identity header, on any route
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(USERNAME_HEADER))
                .build();

        if (PUBLIC_PATHS.contains(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        String username;
        try {
            username = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected JWT: {}", e.getClass().getSimpleName());   // the token itself is never logged
            return unauthorized(exchange);
        }

        // A correctly signed token without a subject must not become a request without an identity
        if (username == null || username.isBlank()) {
            return unauthorized(exchange);
        }

        ServerHttpRequest authenticated = sanitized.mutate()
                .headers(headers -> headers.set(USERNAME_HEADER, username))
                .build();
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}