package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GatewayDnsResolutionConfigTest {

    @Test
    void boundedResolverCanBeAppliedToGatewayHttpClient() {
        var customizer = new GatewayDnsResolutionConfig(Duration.ofSeconds(2))
                .boundGatewayDnsResolver();
        HttpClient original = HttpClient.create();
        HttpClient updated = customizer.customize(original);
        assertNotNull(updated);
        assertNotSame(original, updated);
    }

    @Test
    void invalidCacheTtlsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayDnsResolutionConfig(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayDnsResolutionConfig(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayDnsResolutionConfig(Duration.ofSeconds(61)));
        assertThrows(IllegalArgumentException.class,
                () -> new GatewayDnsResolutionConfig(null));
    }
}
