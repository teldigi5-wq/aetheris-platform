package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeAudienceTokenIssuerTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "runtime-test-key-v1";
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-10-29T15:00:00Z"), ZoneOffset.UTC);

    private static KeyPair rsa(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Synthetic RSA test key failed", error);
        }
    }

    private static MockEnvironment config(KeyPair pair) {
        return new MockEnvironment()
            .withProperty("aetheris.runtime-token.pkcs8-base64",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
            .withProperty("aetheris.runtime-token.key-id", KID)
            .withProperty("aetheris.runtime-token.issuer", ISSUER)
            .withProperty("aetheris.runtime-token.audience", AUDIENCE);
    }

    private static IdentityAccount account(Role role) {
        var a = new IdentityAccount(
            "Test-Only", "synthetic@example.invalid", "unusable-test-hash", role);
        ReflectionTestUtils.setField(a, "id", 42L);
        return a;
    }

    @Test
    void unconfiguredAndPartialOperatorTrustNeverIssueRuntimeCredentials() {
        var disabled = new RuntimeAudienceTokenIssuer(new MockEnvironment(), CLOCK);
        assertFalse(disabled.isConfigured());
        assertThrows(IllegalStateException.class,
            () -> disabled.issueForVerifiedExchange(account(Role.DEVELOPER)));

        var incomplete = new MockEnvironment()
            .withProperty("aetheris.runtime-token.key-id", KID);
        assertThrows(IllegalStateException.class,
            () -> new RuntimeAudienceTokenIssuer(incomplete, CLOCK));
        var corrupt = config(rsa(2048))
            .withProperty("aetheris.runtime-token.pkcs8-base64", "NOT_A_KEY%%");
        assertThrows(IllegalStateException.class,
            () -> new RuntimeAudienceTokenIssuer(corrupt,CLOCK));
        var weak = config(rsa(1024));
        assertThrows(IllegalStateException.class,
            () -> new RuntimeAudienceTokenIssuer(weak,CLOCK));
        var wrongAudience = config(rsa(2048))
            .withProperty("aetheris.runtime-token.audience","bad audience!");
        assertThrows(IllegalStateException.class,
            () -> new RuntimeAudienceTokenIssuer(wrongAudience,CLOCK));
    }

    @Test
    void genuineRs256RuntimeAudienceClaimsAndLifetimeMatchVerifierRequirements() {
        var pair = rsa(2048);
        var issuer = new RuntimeAudienceTokenIssuer(config(pair), CLOCK);
        assertTrue(issuer.isConfigured());
        var signed = Jwts.parser()
            .verifyWith(pair.getPublic())
            .clock(() -> java.util.Date.from(CLOCK.instant()))
            .build()
            .parseSignedClaims(issuer.issueForVerifiedExchange(account(Role.DEVELOPER)));
        assertEquals("RS256",signed.getHeader().getAlgorithm());
        assertEquals(KID,signed.getHeader().getKeyId());
        var claims=signed.getPayload();
        assertEquals(ISSUER,claims.getIssuer());
        assertEquals(Set.of(AUDIENCE),claims.getAudience());
        assertEquals("synthetic@example.invalid",claims.getSubject());
        assertEquals(42L,((Number)claims.get("uid")).longValue());
        assertEquals("DEVELOPER",claims.get("role",String.class));
        assertEquals(Set.of("orchestrator:read","orchestrator:write"),
            Set.copyOf((List<?>)claims.get("scopes",List.class)));
        assertNotNull(claims.getId());
        assertFalse(claims.getId().isBlank());
        assertEquals(Duration.ofMinutes(5),
            Duration.between(claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()));
    }

    @Test
    void apiConsumerNeverReceivesWriteOrOtherServiceScopes() {
        var pair = rsa(2048);
        var issuer = new RuntimeAudienceTokenIssuer(config(pair), CLOCK);
        var claims=Jwts.parser()
            .verifyWith(pair.getPublic())
            .clock(() -> java.util.Date.from(CLOCK.instant()))
            .build()
            .parseSignedClaims(issuer.issueForVerifiedExchange(account(Role.API_CONSUMER)))
            .getPayload();
        assertEquals("API_CONSUMER",claims.get("role",String.class));
        assertEquals(List.of("orchestrator:read"),claims.get("scopes",List.class));
        assertFalse(((List<?>)claims.get("scopes",List.class)).contains("users:read"));
        assertFalse(((List<?>)claims.get("scopes",List.class)).contains("orchestrator:write"));
    }

    @Test
    void tokenCannotBeVerifiedByUnrelatedRsaKeyOrHmacVerifier() {
        var key = rsa(2048);
        var issuer = new RuntimeAudienceTokenIssuer(config(key), CLOCK);
        String signed = issuer.issueForVerifiedExchange(account(Role.ADMIN));
        var unrelated = rsa(2048);
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(unrelated.getPublic())
            .clock(() -> java.util.Date.from(CLOCK.instant()))
            .build().parseSignedClaims(signed));
        var hmac = Keys.hmacShaKeyFor(
            "a-test-only-mac-key-longer-than-32-bytes".getBytes(StandardCharsets.UTF_8));
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(hmac)
            .clock(() -> java.util.Date.from(CLOCK.instant()))
            .build().parseSignedClaims(signed));
    }

    @Test
    void persistedAccountIdentityRequiredAndLegacyHmacJwtServiceUnchanged() {
        var pair = rsa(2048);
        var issuer=new RuntimeAudienceTokenIssuer(config(pair),CLOCK);
        assertThrows(IllegalArgumentException.class,
            () -> issuer.issueForVerifiedExchange(null));
        var unsaved = new IdentityAccount("Test","synthetic@example.invalid","x",
            Role.API_CONSUMER);
        assertThrows(IllegalArgumentException.class,
            () -> issuer.issueForVerifiedExchange(unsaved));

        // Existing production browser/Gateway token behavior remains separate.
        var legacy = new JwtService("a-legacy-test-only-secret-longer-than-32-chars",3600);
        String hs=legacy.issue(account(Role.API_CONSUMER));
        var header = hs.split("\\.")[0];
        String json=new String(Base64.getUrlDecoder().decode(header),
            StandardCharsets.UTF_8);
        assertTrue(json.contains("\"alg\":\"HS256\""));
        assertFalse(json.contains("\"kid\""));
    }
}
