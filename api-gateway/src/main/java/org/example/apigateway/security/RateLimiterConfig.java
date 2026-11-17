package org.example.apigateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    // Keys are namespaced ("user:" / "ip:"). Redis stores the buckets under the bare key, so without a
    // prefix a user literally named like an IP address would share that IP's login bucket.

    /** One bucket per authenticated user (identity header set by JwtGatewayFilter), else per client IP. */
    @Primary
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String username = exchange.getRequest().getHeaders().getFirst("X-Auth-Username");
            if (username != null && !username.isBlank()) {
                return Mono.just("user:" + username);
            }
            return Mono.just(clientKey(exchange));
        };
    }

    /** One bucket per client IP: for endpoints reached before a token exists (login, register). */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientKey(exchange));
    }

    private static String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return "ip:" + remote.getAddress().getHostAddress();
        }
        return "ip:unknown";
    }
}