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
            String userId = exchange.getRequest().getHeaders().getFirst("X-Aetheris-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just("ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }

            return Mono.just("anonymous");
        };
    }
}
