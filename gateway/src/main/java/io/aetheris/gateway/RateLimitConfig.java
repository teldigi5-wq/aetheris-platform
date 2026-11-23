package io.aetheris.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    @Bean
    KeyResolver aetherisKeyResolver() {
        return exchange -> {
            // Only the JWT verifier may populate this server-side exchange attribute.
            // Client-supplied X-Aetheris-User-Id must never select a rate-limit bucket:
            // /api/auth/** is intentionally unauthenticated.
            String verifiedUserId = exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE);
            if (verifiedUserId != null && !verifiedUserId.isBlank()) {
                return Mono.just("user:" + verifiedUserId);
            }

            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }
}
