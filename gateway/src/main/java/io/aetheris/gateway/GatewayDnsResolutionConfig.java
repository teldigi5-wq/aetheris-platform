package io.aetheris.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bound Reactor Netty's DNS cache so restarted Compose/Kubernetes service
 * endpoints are re-resolved without restarting the Gateway. This does not
 * disable TLS, retry unsafe methods, or change authorization boundaries.
 */
@Configuration
public class GatewayDnsResolutionConfig {
    private final Duration maxTtl;

    public GatewayDnsResolutionConfig(
            @Value("${aetheris.gateway.dns.cache-max-ttl:2s}") Duration maxTtl) {
        if (maxTtl == null || maxTtl.compareTo(Duration.ofSeconds(1)) < 0
                || maxTtl.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("Gateway DNS cache max TTL must be between 1s and 60s");
        }
        this.maxTtl = maxTtl;
    }

    @Bean
    HttpClientCustomizer boundGatewayDnsResolver() {
        return httpClient -> httpClient.resolver(resolver -> resolver
                .cacheMaxTimeToLive(maxTtl)
                .cacheMinTimeToLive(Duration.ZERO)
                .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
