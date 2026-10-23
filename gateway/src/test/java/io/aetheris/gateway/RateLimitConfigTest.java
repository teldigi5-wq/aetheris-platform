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
                        .header("X-Aetheris-User-Id", "999")
                        .remoteAddress(new InetSocketAddress("203.0.113.10", 443))
                        .build());
        exchange.getAttributes().put(JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE, "42");

        assertEquals("user:42", resolver.resolve(exchange).block());
    }

    @Test
    void forgedIdentityHeaderCannotBypassPerIpLimiterOnAuthRoute() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange first = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Aetheris-User-Id", "attacker-1")
                        .remoteAddress(new InetSocketAddress("203.0.113.25", 43001))
                        .build());
        MockServerWebExchange second = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Aetheris-User-Id", "attacker-2")
                        .remoteAddress(new InetSocketAddress("203.0.113.25", 43002))
                        .build());

        assertEquals("ip:203.0.113.25", resolver.resolve(first).block());
        assertEquals(resolver.resolve(first).block(), resolver.resolve(second).block());
    }

    @Test
    void forgedIdentityHeaderIsIgnoredOnProtectedRoutesWithoutVerification() {
        KeyResolver resolver = new RateLimitConfig().aetherisKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Aetheris-User-Id", "admin")
                        .remoteAddress(new InetSocketAddress("203.0.113.26", 43001))
                        .build());

        assertEquals("ip:203.0.113.26", resolver.resolve(exchange).block());
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
