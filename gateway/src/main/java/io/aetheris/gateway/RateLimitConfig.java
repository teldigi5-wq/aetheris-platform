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
            // Never trust caller-controlled X-Aetheris headers for rate limiting.
            // Auth routes have no verified principal and are limited by remote IP.
            String verifiedId = exchange.getAttribute(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE);
            if (verifiedId != null && !verifiedId.isBlank()) {
                return Mono.just("user:" + verifiedId);
            }

            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }
}
