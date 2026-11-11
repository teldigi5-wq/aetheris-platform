package io.aetheris.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Per-principal key only when the gateway itself verified a JWT for this request
     * (exchange attribute set by {@link JwtAuthenticationFilter}). Request headers are never
     * trusted here: a client-chosen X-Aetheris-User-Id would otherwise give every request a
     * fresh bucket. Everything else, including login and registration, is keyed by peer IP.
     */
    @Bean
    KeyResolver aetherisKeyResolver() {
        return exchange -> {
            Object userId = exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE);
            if (userId instanceof String id && !id.isBlank()) {
                return Mono.just("user:" + id);
            }

            if (exchange.getRequest().getRemoteAddress() != null
                    && exchange.getRequest().getRemoteAddress().getAddress() != null) {
                return Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }
}
