package io.aetheris.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitConfigTest {

    @Test
    void verifiedUserIdAttributeTakesPrecedenceOverRemoteAddress() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 443))
                        .build());
        exchange.getAttributes().put(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE, "42");

        assertEquals("user:42", resolver.resolve(exchange).block());
    }

    @Test
    void clientSuppliedUserIdHeaderIsIgnored() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Aetheris-User-Id", "42")
                        .remoteAddress(new InetSocketAddress("203.0.113.12", 443))
                        .build());

        assertEquals("ip:203.0.113.12", resolver.resolve(exchange).block());
    }

    @Test
    void rotatingSpoofedHeadersFromOneIpShareOneBucket() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        Set<String> keys = new HashSet<>();
        for (int attempt = 0; attempt < 50; attempt++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/auth/login")
                            .header("X-Aetheris-User-Id", "spoof-" + attempt)
                            .remoteAddress(new InetSocketAddress("203.0.113.13", 443))
                            .build());
            keys.add(resolver.resolve(exchange).block());
        }

        assertEquals(Set.of("ip:203.0.113.13"), keys);
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
