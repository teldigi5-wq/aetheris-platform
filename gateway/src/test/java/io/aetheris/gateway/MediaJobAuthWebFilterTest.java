package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MediaJobAuthWebFilterTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static String token(Object uid, List<String> scopes, Instant expiration) {
        return Jwts.builder()
                .subject("verified@example.com").claim("role", "DEVELOPER")
                .claim("uid", uid).claim("scopes", scopes)
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static String valid(String... scopes) {
        return token(42L, List.of(scopes), Instant.now().plusSeconds(300));
    }

    private static MockServerHttpRequest.BaseBuilder<?> get(String token) {
        return MockServerHttpRequest.get("/api/media/capabilities")
                .header("X-Aetheris-Client", "syntra-native")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static AtomicReference<ServerWebExchange> apply(MockServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        new MediaJobAuthWebFilter(SECRET).filter(exchange, accepted -> {
            seen.set(accepted);
            return Mono.empty();
        }).block();
        return seen;
    }

    @Test
    void signedUidIsVerifiedAndForgedOwnerHeaderCannotReplaceIt() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                get(valid("media:read")).header("X-Aetheris-Owner-Id", "victim")
                        .header("X-Aetheris-User-Id", "999").build());
        var forwarded = apply(exchange);
        assertNotNull(forwarded.get());
        assertEquals(Long.valueOf(42L), forwarded.get().getAttribute(MediaJobAuthWebFilter.VERIFIED_OWNER_ATTRIBUTE));
        assertNotEquals(Long.valueOf(999L), forwarded.get().getAttribute(MediaJobAuthWebFilter.VERIFIED_OWNER_ATTRIBUTE));
    }

    @Test
    void missingAndExpiredAndMalformedBearerFailClosed() {
        var missing = MockServerWebExchange.from(MockServerHttpRequest.get("/api/media/capabilities")
                .header("X-Aetheris-Client", "syntra-native").build());
        assertNull(apply(missing).get());
        assertEquals(HttpStatus.UNAUTHORIZED, missing.getResponse().getStatusCode());

        var expired = MockServerWebExchange.from(get(
                token(42L, List.of("media:read"), Instant.now().minusSeconds(120))).build());
        assertNull(apply(expired).get());
        assertEquals(HttpStatus.UNAUTHORIZED, expired.getResponse().getStatusCode());

        var malformed = MockServerWebExchange.from(get("bad.signature").build());
        assertNull(apply(malformed).get());
        assertEquals(HttpStatus.UNAUTHORIZED, malformed.getResponse().getStatusCode());
    }

    @Test
    void stringAndNegativeUidAreNeverOwners() {
        for (Object uid : List.of("42", -1L, 0L)) {
            var exchange = MockServerWebExchange.from(get(
                    token(uid, List.of("media:read"), Instant.now().plusSeconds(300))).build());
            assertNull(apply(exchange).get());
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void missingScopeDeniedAndMethodScopesDiffer() {
        var read = MockServerWebExchange.from(get(valid("orchestrator:read")).build());
        assertNull(apply(read).get());
        assertEquals(HttpStatus.FORBIDDEN, read.getResponse().getStatusCode());

        var write = MockServerWebExchange.from(MockServerHttpRequest.post("/api/media/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid("media:read"))
                .header("X-Aetheris-Client", "syntra-native")
                .contentType(MediaType.APPLICATION_JSON).body("{}"));
        assertNull(apply(write).get());
        assertEquals(HttpStatus.FORBIDDEN, write.getResponse().getStatusCode());

        var allowed = MockServerWebExchange.from(MockServerHttpRequest.post("/api/media/jobs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid("media:write"))
                .header("X-Aetheris-Client", "syntra-native")
                .contentType(MediaType.APPLICATION_JSON).body("{}"));
        assertNotNull(apply(allowed).get());
    }

    @Test
    void originCookieAndMissingNativeMarkerRefused() {
        var origin = MockServerWebExchange.from(get(valid("media:read"))
                .header(HttpHeaders.ORIGIN, "http://localhost:3000").build());
        assertNull(apply(origin).get());
        assertEquals(HttpStatus.FORBIDDEN, origin.getResponse().getStatusCode());
        var cookie = MockServerWebExchange.from(get(valid("media:read"))
                .header(HttpHeaders.COOKIE, "session=abc").build());
        assertNull(apply(cookie).get());
        assertEquals(HttpStatus.FORBIDDEN, cookie.getResponse().getStatusCode());
        var unmarked = MockServerWebExchange.from(MockServerHttpRequest.get("/api/media/capabilities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + valid("media:read")).build());
        assertNull(apply(unmarked).get());
        assertEquals(HttpStatus.FORBIDDEN, unmarked.getResponse().getStatusCode());
    }

    @Test
    void methodPayloadAndEncodedRouteBoundaryFailClosed() {
        var put = MockServerWebExchange.from(MockServerHttpRequest.put("/api/media/jobs")
                .header("X-Aetheris-Client", "syntra-native").build());
        assertNull(apply(put).get());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, put.getResponse().getStatusCode());

        var wrongType = MockServerWebExchange.from(MockServerHttpRequest.post("/api/media/jobs")
                .header("X-Aetheris-Client", "syntra-native").contentType(MediaType.TEXT_PLAIN).body("stuff"));
        assertNull(apply(wrongType).get());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, wrongType.getResponse().getStatusCode());
    }

    @Test
    void otherRoutesRemainControlledByTheirExistingFilters() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());
        assertNotNull(apply(exchange).get());
    }

    @Test
    void weakSigningSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MediaJobAuthWebFilter("short"));
    }
}
