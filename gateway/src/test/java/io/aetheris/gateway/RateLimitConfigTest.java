package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitConfigTest {

    @Test
    void verifiedUserIdTakesPrecedenceOverRemoteAddress() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Aetheris-User-Id", "42")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 443))
                        .build());

        assertEquals("user:42", resolver.resolve(exchange).block());
    }

    @Test
    void remoteIpIsUsedWhenNoUserIdentityExists() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .remoteAddress(new InetSocketAddress("203.0.113.11", 443))
                        .build());

        assertEquals("ip:203.0.113.11", resolver.resolve(exchange).block());
    }

    @Test
    void anonymousKeyIsUsedWhenNeitherUserNorRemoteAddressExists() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());

        assertEquals("anonymous", resolver.resolve(exchange).block());
    }
}
