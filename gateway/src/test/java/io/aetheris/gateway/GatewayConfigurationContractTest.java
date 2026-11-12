package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GatewayConfigurationContractTest {

    @Test
    void cloudGatewayWebFluxRoutesRemainConfiguredAfterMajorUpgrade() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        assertFalse(sources.isEmpty());
        PropertySource<?> config = sources.get(0);
        // The strict credential route must be matched before the catch-all identity route.
        assertEquals("identity-credentials",
                config.getProperty("spring.cloud.gateway.server.webflux.routes[0].id"));
        // Its 1/s, burst 10 limit is asserted by tests/test_gateway_auth_rate_limit.py.
        assertEquals("identity-service",
                config.getProperty("spring.cloud.gateway.server.webflux.routes[1].id"));
        assertEquals("user-service-read",
                config.getProperty("spring.cloud.gateway.server.webflux.routes[2].id"));
        assertEquals("orchestrator-service",
                config.getProperty("spring.cloud.gateway.server.webflux.routes[5].id"));
        assertNull(config.getProperty("spring.cloud.gateway.routes[0].id"));
        assertEquals("2000",
                String.valueOf(config.getProperty("spring.cloud.gateway.server.webflux.httpclient.connect-timeout")));
    }
}
