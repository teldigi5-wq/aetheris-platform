package io.aetheris.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Gateway GlobalFilter verifies RS256 only in configured public-key mode. */
class GatewayRs256VerificationTest {
    private static final String SECRET = "SYNTHETIC_LEGACY_GATEWAY_HMAC_TEST_SECRET_20261027_LONG";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String USERS = "aetheris-user-api";
    private static final String AUDIT = "aetheris-audit-api";
    private static final String ORCH = "aetheris-orchestrator-api";

    private static KeyPair pair(int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(bits);
        return g.generateKeyPair();
    }

    private static String pem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private String signed(KeyPair keyPair, String audience) {
        Instant now = Instant.now();
        var b = Jwts.builder().subject("admin@example.invalid")
            .claim("uid", 42L).claim("role", "ADMIN")
            .claim("scopes", List.of("users:read"))
            .issuer(ISSUER).claim("aud", List.of(audience))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)));
        return keyPair == null
            ? b.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact()
            : b.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private void assertRoute(JwtAuthenticationFilter filter, String jwt,
                             HttpStatus failure) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        }).block();
        if (failure == null) {
            assertNotNull(forwarded.get());
        } else {
            assertNull(forwarded.get());
            assertEquals(failure, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void rsaPublicKeyAcceptsMatchingIssuerAndSoleAudience() throws Exception {
        KeyPair good = pair(2048);
        var filter = new JwtAuthenticationFilter(SECRET, ISSUER, USERS, AUDIT, ORCH, pem(good));
        assertRoute(filter, signed(good, USERS), null);
        assertRoute(filter, signed(good, AUDIT), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rsaOnlyRejectsLegacyHmacAndWrongPrivateKeyWithoutDowngrade() throws Exception {
        KeyPair good = pair(2048);
        KeyPair wrong = pair(2048);
        var filter = new JwtAuthenticationFilter(SECRET, ISSUER, USERS, AUDIT, ORCH, pem(good));
        assertRoute(filter, signed(null, USERS), HttpStatus.UNAUTHORIZED);
        assertRoute(filter, signed(wrong, USERS), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actualGatewayAcceptsPinnedOverlapThenRejectsRetiredSigner() throws Exception {
        KeyPair older = pair(2048), newer = pair(2048);
        var overlap = new JwtAuthenticationFilter(
            SECRET, ISSUER, USERS, AUDIT, ORCH,
            pem(older), "2026-old", pem(newer), "2026-new");
        Instant now = Instant.now();
        String oldToken = Jwts.builder().header().keyId("2026-old").and()
            .subject("admin@example.invalid").claim("uid", 42L)
            .claim("role", "ADMIN").claim("scopes", List.of("users:read"))
            .issuer(ISSUER).claim("aud", List.of(USERS))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(older.getPrivate(), Jwts.SIG.RS256).compact();
        String newToken = Jwts.builder().header().keyId("2026-new").and()
            .subject("admin@example.invalid").claim("uid", 42L)
            .claim("role", "ADMIN").claim("scopes", List.of("users:read"))
            .issuer(ISSUER).claim("aud", List.of(USERS))
            .issuedAt(Date.from(now.minusSeconds(5)))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(newer.getPrivate(), Jwts.SIG.RS256).compact();
        assertRoute(overlap, oldToken, null);
        assertRoute(overlap, newToken, null);
        var retired = new JwtAuthenticationFilter(
            SECRET, ISSUER, USERS, AUDIT, ORCH,
            pem(newer), "2026-new", "", "");
        assertRoute(retired, oldToken, HttpStatus.UNAUTHORIZED);
        assertRoute(retired, newToken, null);
    }

    @Test
    void weakMalformedAndUntargetedPublicKeyPolicyFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new JwtAuthenticationFilter(SECRET, ISSUER, USERS, AUDIT, ORCH, "broken-key"));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtAuthenticationFilter(SECRET, ISSUER, USERS, AUDIT, ORCH, pem(pair(1024))));
        KeyPair good = pair(2048);
        // A configured public key cannot accept untargeted/legacy tokens when
        // issuer or route target is missing.
        var incomplete = new JwtAuthenticationFilter(SECRET, "", "", "", "", pem(good));
        assertRoute(incomplete, signed(good, USERS), HttpStatus.SERVICE_UNAVAILABLE);
    }
}
