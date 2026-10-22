package org.example.apigateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver userKeyResolver() {
        return  exchange -> {
            String username = exchange.getRequest().getHeaders().getFirst("X-Auth-Username");
            if (username != null && !username.isBlank()) {
                return Mono.just(username);
            }

            if (exchange.getRequest().getRemoteAddress() != null &&
                    exchange.getRequest().getRemoteAddress().getAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");

        };
    }
}
