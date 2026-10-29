package io.aetheris.identity;

import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S0-E6E cross-repository source-identity interoperability tests.
 *
 * The Platform issuer under test is compiled from the checked-out Platform
 * branch. RuntimePinnedPrincipalVerifier is copied from the immutable SHA
 * of the ACTUAL Runtime repository during CI; it is NEVER vendored here.
 *
 * Tokens, RSA private keys and accounts are entirely synthetic test fixtures.
 * This test cannot issue user tokens, restore HTTP routes or grant ownership.
 */
class RuntimePlatformRuntimeCrossRepoRoundTripTest {
    private static final String ISSUER = "https://identity.aetheris.example.invalid";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "s0-e6e-fixed-test-kid";
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-10-29T18:00:00Z"), ZoneOffset.UTC);

    private static KeyPair rsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot generate disposable test RSA", ex);
        }
    }

    private static MockEnvironment issuerProperties(KeyPair pair,
            String kid, String issuer, String audience) {
        return new MockEnvironment()
            .withProperty("aetheris.runtime-token.pkcs8-base64",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
            .withProperty("aetheris.runtime-token.key-id", kid)
            .withProperty("aetheris.runtime-token.issuer", issuer)
            .withProperty("aetheris.runtime-token.audience", audience);
    }

    private static String publicKeyPem(KeyPair pair) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static RuntimeAudienceTokenIssuer issuer(KeyPair pair) {
        return new RuntimeAudienceTokenIssuer(
            issuerProperties(pair, KID, ISSUER, AUDIENCE), CLOCK);
    }

    private static RuntimePinnedPrincipalVerifier runtimeVerifier(KeyPair pair) {
        return new RuntimePinnedPrincipalVerifier(
            publicKeyPem(pair), KID, ISSUER, AUDIENCE, CLOCK);
    }

    private static IdentityAccount syntheticAccount(Role role, long uid) {
        var account = new IdentityAccount(
            "Disposable test identity", "synthetic-" + uid + "@example.invalid",
            "unusable-hash", role);
        ReflectionTestUtils.setField(account, "id", uid);
        return account;
    }

    @Test
    void actualPlatformIssuerMatchesActualRuntimeVerifierForAllThreeRoles() {
        KeyPair pair = rsa();
        var signing = issuer(pair);
        var verifying = runtimeVerifier(pair);
        for (Role role : Role.values()) {
            long uid = role.ordinal() + 100L;
            var account = syntheticAccount(role, uid);
            String token = signing.issueForVerifiedExchange(account);
            var principal = verifying.verifyBearer("Bearer " + token);
            assertEquals(uid, principal.identityUid());
            assertEquals(account.getEmail(), principal.subject());
            assertEquals(role.name(), principal.role());
            Set<String> expected = role.scopes().stream()
                .filter(x -> x.equals("orchestrator:read")
                    || x.equals("orchestrator:write"))
                .collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, principal.scopes());
            assertEquals(expected.contains("orchestrator:write"),
                principal.hasScope("orchestrator:write"));
        }
        System.out.println("AETHERIS_S0_E6E_REAL_SOURCE_PLATFORM_TO_RUNTIME_RS256_ROUNDTRIP_PASS");
    }

    @Test
    void correctlySignedTokenHasBoundedAudienceAndFiveMinuteLifetime() {
        KeyPair pair = rsa();
        String token = issuer(pair).issueForVerifiedExchange(
            syntheticAccount(Role.DEVELOPER, 211L));
        var signed = Jwts.parser()
            .verifyWith(pair.getPublic())
            .clock(() -> Date.from(CLOCK.instant()))
            .build().parseSignedClaims(token);
        assertEquals("RS256", signed.getHeader().getAlgorithm());
        assertEquals(KID, signed.getHeader().getKeyId());
        assertEquals(ISSUER, signed.getPayload().getIssuer());
        assertEquals(Set.of(AUDIENCE), signed.getPayload().getAudience());
        assertEquals(CLOCK.instant().plusSeconds(300),
            signed.getPayload().getExpiration().toInstant());
        assertNotNull(signed.getPayload().getId());
        assertFalse(signed.getPayload().getId().isBlank());
        var runtime = runtimeVerifier(pair);
        assertTrue(runtime.verifyBearer("Bearer " + token)
            .hasScope("orchestrator:read"));
        var afterExpiry = new RuntimePinnedPrincipalVerifier(
            publicKeyPem(pair), KID, ISSUER, AUDIENCE,
            Clock.offset(CLOCK, java.time.Duration.ofSeconds(301)));
        assertThrows(IllegalArgumentException.class,
            () -> afterExpiry.verifyBearer("Bearer " + token));
    }

    @Test
    void operatorPinsCannotBeReplacedByCallerSignedHeaderOrForeignPublicKey() {
        KeyPair trusted = rsa();
        KeyPair attacker = rsa();
        String valid = issuer(trusted).issueForVerifiedExchange(
            syntheticAccount(Role.API_CONSUMER, 301L));
        String foreignSigned = issuer(attacker).issueForVerifiedExchange(
            syntheticAccount(Role.ADMIN, 999L));
        assertThrows(IllegalArgumentException.class,
            () -> runtimeVerifier(attacker).verifyBearer("Bearer " + valid));
        assertThrows(IllegalArgumentException.class,
            () -> runtimeVerifier(trusted).verifyBearer("Bearer " + foreignSigned));
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                publicKeyPem(trusted), "different-kid", ISSUER, AUDIENCE, CLOCK)
                .verifyBearer("Bearer " + valid));
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                publicKeyPem(trusted), KID, "https://untrusted.invalid", AUDIENCE, CLOCK)
                .verifyBearer("Bearer " + valid));
        assertThrows(IllegalArgumentException.class,
            () -> new RuntimePinnedPrincipalVerifier(
                publicKeyPem(trusted), KID, ISSUER, "unrelated-service", CLOCK)
                .verifyBearer("Bearer " + valid));
        assertThrows(IllegalArgumentException.class,
            () -> runtimeVerifier(trusted).verifyBearer(
                "bearer " + valid));
    }

    @Test
    void signedRoleEscalationAndUnexpectedAudienceAlwaysRejected() {
        KeyPair pair = rsa();
        var verifying = runtimeVerifier(pair);
        String badScopes = Jwts.builder().header().keyId(KID).and()
            .subject("synthetic@example.invalid").issuer(ISSUER)
            .claim("aud", List.of(AUDIENCE)).claim("uid", 42L)
            .claim("role", "API_CONSUMER")
            .claim("scopes", List.of("orchestrator:read", "orchestrator:write"))
            .issuedAt(Date.from(CLOCK.instant()))
            .expiration(Date.from(CLOCK.instant().plusSeconds(300)))
            .signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
        String extraAudience = Jwts.builder().header().keyId(KID).and()
            .subject("synthetic@example.invalid").issuer(ISSUER)
            .claim("aud", List.of(AUDIENCE, "unrelated-service"))
            .claim("uid", 42L).claim("role", "ADMIN")
            .claim("scopes", List.of("orchestrator:read"))
            .issuedAt(Date.from(CLOCK.instant()))
            .expiration(Date.from(CLOCK.instant().plusSeconds(300)))
            .signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThrows(IllegalArgumentException.class,
            () -> verifying.verifyBearer("Bearer " + badScopes));
        assertThrows(IllegalArgumentException.class,
            () -> verifying.verifyBearer("Bearer " + extraAudience));
    }

    @Test
    void actualLegacyPlatformHmacAccessTokenIsNotRuntimeRs256Token() {
        KeyPair pair = rsa();
        var legacy = new JwtService(
            "test-only-hmac-secret-longer-than-thirty-two-characters", 3600);
        String hmac = legacy.issue(syntheticAccount(Role.DEVELOPER, 771L));
        var headerJson = new String(Base64.getUrlDecoder()
            .decode(hmac.split("\\.")[0]), StandardCharsets.UTF_8);
        assertTrue(headerJson.contains("\"alg\":\"HS256\""));
        assertThrows(IllegalArgumentException.class,
            () -> runtimeVerifier(pair).verifyBearer("Bearer " + hmac));
        // Proves legacy auth remains intentionally separate; it does not
        // authenticate any ordinary Platform user into the Runtime.
    }

    @Test
    void unusedIssuerCannotProduceTokenWithoutExplicitOperatorConfiguration() {
        var disabled = new RuntimeAudienceTokenIssuer(new MockEnvironment(), CLOCK);
        assertFalse(disabled.isConfigured());
        assertThrows(IllegalStateException.class,
            () -> disabled.issueForVerifiedExchange(
                syntheticAccount(Role.ADMIN, 900L)));
        var partial = new MockEnvironment()
            .withProperty("aetheris.runtime-token.key-id", KID);
        assertThrows(IllegalStateException.class,
            () -> new RuntimeAudienceTokenIssuer(partial, CLOCK));
    }
}
