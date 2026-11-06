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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class JwtRs256ModeTest {
    private static final java.security.KeyPair PAIR = newPair();
    private static final String PEM = pem("PUBLIC KEY", PAIR.getPublic().getEncoded());
    private static final String KID = "aetheris-rs256-review-a";
    private static final String ISS = JwtAuthenticationFilter.DEFAULT_ISSUER;
    private static final String AUD = JwtAuthenticationFilter.DEFAULT_AUDIENCE;
    private static java.security.KeyPair newPair() {
        try {
            var gen = java.security.KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (java.security.GeneralSecurityException failure) {
            throw new AssertionError("Synthetic RSA key generation unavailable");
        }
    }

    private static String pem(String kind, byte[] bytes) {
        return "-----BEGIN " + kind + "-----\\n" +
                java.util.Base64.getEncoder().encodeToString(bytes) +
                "\\n-----END " + kind + "-----";
    }

    private static String token(String role, String kid, String issuer, String audience, boolean hs) {
        var builder = Jwts.builder().subject("synthetic@example.test")
                .issuer(issuer).audience().add(audience).and()
                .claim("uid", 19L).claim("role", role)
                .claim("scopes", List.of("users:read"))
                .issuedAt(Date.from(Instant.now().minusSeconds(15)))
                .expiration(Date.from(Instant.now().plusSeconds(180)));
        if (kid != null) builder.header().keyId(kid).and();
        return hs ? builder.signWith(Keys.hmacShaKeyFor(
                        "synthetic-hmac-key-with-minimum-32bytes-123".getBytes(StandardCharsets.UTF_8)),
                        Jwts.SIG.HS256).compact()
                : builder.signWith(PAIR.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static HttpStatus status(String token) {
        var filter = new JwtAuthenticationFilter("", ISS, AUD, "RS256", PEM, KID);
        var request = MockServerHttpRequest.get("/api/users").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token).build();
        var exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        filter.filter(exchange, next -> {forwarded.set(next); return Mono.empty();}).block();
        return forwarded.get() == null ? (HttpStatus)exchange.getResponse().getStatusCode() : HttpStatus.OK;
    }

    @Test
    void signedAdministratorMayReadGlobalApi() {
        assertEquals(HttpStatus.OK, status(token("ADMIN", KID, ISS, AUD, false)));
    }

    @Test
    void signedConsumerCannotReadGlobalApiDespiteScopedJwt() {
        assertEquals(HttpStatus.FORBIDDEN, status(token("API_CONSUMER", KID, ISS, AUD, false)));
    }

    @Test
    void wrongKeyIdIssuerAudienceOrAlgorithmCannotDowngrade() {
        for (String token : List.of(
                token("ADMIN", "not-pinned", ISS, AUD, false),
                token("ADMIN", null, ISS, AUD, false),
                token("ADMIN", KID, "forged-identity", AUD, false),
                token("ADMIN", KID, ISS, "different-audience", false),
                token("ADMIN", KID, ISS, AUD, true))) {
            assertEquals(HttpStatus.UNAUTHORIZED, status(token));
        }
    }

    @Test
    void unsupportedModeOrBothSigningKeysFailAtStartup() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtAuthenticationFilter("legacy-secret-long-enough-for-hmac-123456",
                        ISS, AUD, "RS256", PEM, KID));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtAuthenticationFilter("", ISS, AUD, "RS256", PEM, "invalid/kid"));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtAuthenticationFilter("", ISS, AUD, "HS256", PEM, KID));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtAuthenticationFilter("", ISS, AUD, "UNSIGNED", PEM, KID));
    }
}
