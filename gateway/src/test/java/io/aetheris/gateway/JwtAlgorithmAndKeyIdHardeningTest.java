package io.aetheris.gateway;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * I6 (B62/S0 readiness §5): algorithm-confusion, key-id and previous-key deadline
 * boundaries for the gateway verifier in BOTH supported modes. All keys are
 * generated in memory per run; no fixture files, no real signing material.
 */
class JwtAlgorithmAndKeyIdHardeningTest {
    private static final String ISSUER = JwtAuthenticationFilter.DEFAULT_ISSUER;
    private static final String AUDIENCE = JwtAuthenticationFilter.DEFAULT_AUDIENCE;
    private static final String HMAC_SECRET = "i6-test-only-hmac-secret-at-least-32-bytes-long";
    private static final String CURRENT_KID = "i6-current";
    private static final String PREVIOUS_KID = "i6-previous";

    private static KeyPair current;
    private static KeyPair previous;
    private static KeyPair attacker;

    @BeforeAll
    static void generateEphemeralKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        current = generator.generateKeyPair();
        previous = generator.generateKeyPair();
        attacker = generator.generateKeyPair();
    }

    private static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now;
        AdjustableClock(Instant initial) { now = new AtomicReference<>(initial); }
        void set(Instant next) { now.set(next); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    private static String b64(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static JwtBuilder claims(Instant now) {
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject("i6-admin@example.invalid")
                .claim("role", "ADMIN")
                .claim("uid", 42L)
                .claim("scopes", List.of("users:read"))
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(300)));
    }

    private static String rs256(KeyPair signer, String kid, Instant now) {
        JwtBuilder builder = claims(now);
        if (kid != null) {
            builder.header().keyId(kid).and();
        }
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String hs256(byte[] secret, Instant now) {
        return claims(now).signWith(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"), Jwts.SIG.HS256).compact();
    }

    private static String unsigned(Instant now) {
        return claims(now).compact(); // alg "none"
    }

    private static boolean forwarded(JwtAuthenticationFilter filter, String token) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        AtomicBoolean routed = new AtomicBoolean(false);
        filter.filter(exchange, next -> { routed.set(true); return Mono.empty(); }).block();
        if (!routed.get()) {
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
        return routed.get();
    }

    private static JwtAuthenticationFilter hmac(Clock clock) {
        return new JwtAuthenticationFilter(HMAC_SECRET, ISSUER, AUDIENCE, "hmac", "", "", "", "", "", clock);
    }

    private static JwtAuthenticationFilter rs256(Clock clock, String cutoffEpoch) {
        return new JwtAuthenticationFilter("", ISSUER, AUDIENCE, "rs256",
                b64(current), CURRENT_KID, b64(previous), PREVIOUS_KID, cutoffEpoch, clock);
    }

    @Test
    void hmacModeAcceptsOnlyItsOwnHs256Signature() {
        Instant now = Instant.now();
        JwtAuthenticationFilter filter = hmac(Clock.systemUTC());
        assertTrue(forwarded(filter, hs256(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), now)), "positive control");
        assertFalse(forwarded(filter, unsigned(now)), "alg none");
        assertFalse(forwarded(filter, rs256(current, CURRENT_KID, now)), "RS256 token in hmac mode");
        assertFalse(forwarded(filter, hs256("a-different-32-byte-hmac-secret-value!!".getBytes(StandardCharsets.UTF_8), now)),
                "wrong HMAC secret");
    }

    @Test
    void rs256ModeRejectsAlgorithmConfusion() {
        Instant now = Instant.now();
        JwtAuthenticationFilter filter = rs256(Clock.systemUTC(), Long.toString(now.plusSeconds(600).getEpochSecond()));
        assertTrue(forwarded(filter, rs256(current, CURRENT_KID, now)), "positive control");
        assertFalse(forwarded(filter, unsigned(now)), "alg none");
        // Classic confusion: HS256 keyed with the verifier's own RSA public key bytes.
        assertFalse(forwarded(filter, hs256(current.getPublic().getEncoded(), now)), "HS256 keyed by RSA public key");
        assertFalse(forwarded(filter, hs256(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), now)), "HS256 in rs256 mode");
        assertFalse(forwarded(filter, rs256(attacker, CURRENT_KID, now)), "untrusted RSA key with trusted kid");
    }

    @Test
    void rs256ModeBindsTheSignedKeyIdToTheVerifyingKey() {
        Instant now = Instant.now();
        JwtAuthenticationFilter filter = rs256(Clock.systemUTC(), Long.toString(now.plusSeconds(600).getEpochSecond()));
        assertFalse(forwarded(filter, rs256(current, null, now)), "missing kid");
        assertFalse(forwarded(filter, rs256(current, "unknown-kid", now)), "unknown kid");
        assertFalse(forwarded(filter, rs256(current, "../../etc/passwd", now)), "path-like kid");
        assertFalse(forwarded(filter, rs256(current, "k".repeat(4096), now)), "oversized kid");
        assertFalse(forwarded(filter, rs256(current, PREVIOUS_KID, now)), "current key, previous kid");
        assertFalse(forwarded(filter, rs256(previous, CURRENT_KID, now)), "previous key, current kid");
        assertTrue(forwarded(filter, rs256(previous, PREVIOUS_KID, now)), "previous key, previous kid, before cutoff");
    }

    @Test
    void previousKeyBoundaryIsExactAtOneSecondResolution() {
        Instant start = Instant.now();
        Instant cutoff = start.plusSeconds(600);
        AdjustableClock clock = new AdjustableClock(start);
        JwtAuthenticationFilter filter = rs256(clock, Long.toString(cutoff.getEpochSecond()));
        Instant exactCutoff = Instant.ofEpochSecond(cutoff.getEpochSecond());

        clock.set(exactCutoff.minusSeconds(1));
        assertTrue(forwarded(filter, rs256(previous, PREVIOUS_KID, clock.instant())), "t-1 accepted");
        assertTrue(forwarded(filter, rs256(current, CURRENT_KID, clock.instant())), "current at t-1");

        clock.set(exactCutoff);
        assertFalse(forwarded(filter, rs256(previous, PREVIOUS_KID, clock.instant())), "t rejected");
        assertTrue(forwarded(filter, rs256(current, CURRENT_KID, clock.instant())), "current at t");

        clock.set(exactCutoff.plusSeconds(1));
        assertFalse(forwarded(filter, rs256(previous, PREVIOUS_KID, clock.instant())), "t+1 rejected");
        assertTrue(forwarded(filter, rs256(current, CURRENT_KID, clock.instant())), "current at t+1");
    }

    @Test
    void misconfiguredModesFailClosedAtStartup() {
        Clock clock = Clock.systemUTC();
        String cutoff = Long.toString(Instant.now().plusSeconds(600).getEpochSecond());
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter(
                "short", ISSUER, AUDIENCE, "hmac", "", "", "", "", "", clock), "HMAC secret under 32 bytes");
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter(
                HMAC_SECRET, ISSUER, AUDIENCE, "rs256", b64(current), CURRENT_KID, "", "", "", clock), "secret in rs256 mode");
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter(
                HMAC_SECRET, ISSUER, AUDIENCE, "hmac", b64(current), "", "", "", "", clock), "public key in hmac mode");
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter(
                "", ISSUER, AUDIENCE, "none", "", "", "", "", "", clock), "unsupported mode");
        assertThrows(IllegalArgumentException.class, () -> new JwtAuthenticationFilter(
                "", ISSUER, AUDIENCE, "rs256", b64(current), "Bad Kid!", "", "", "", clock), "malformed configured kid");
        assertNotNull(rs256(clock, cutoff));
    }
}
