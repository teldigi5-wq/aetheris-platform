package io.aetheris.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review-only real Identity JwtService issuance contract for Runtime's
 * opt-in RuntimePinnedPrincipalVerifier.verifyBearerForRevocation.
 *
 * No real Identity or Runtime deployment, B62 read, session registration,
 * resource-owner grant, service endpoint or private key export occurs here.
 * HMAC is retained for legacy compatibility, but MUST NOT be treated as a
 * credential accepted by Runtime's RS256-only verifier.
 */
class IdentityRuntimeSignedJtiContractTest {
    private static final String ISSUER = "aetheris-identity";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "runtime-contract-test";
    private static final String LEGACY_KEY =
        "test-only-legacy-hmac-key-at-least-thirty-two-bytes-long";
    private static KeyPair signingKey;
    private static KeyPair unrelatedKey;

    @BeforeAll
    static void createOnlyEphemeralSigningKeys() throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA");
        keys.initialize(3072);
        signingKey = keys.generateKeyPair();
        unrelatedKey = keys.generateKeyPair();
    }

    private static IdentityAccount syntheticAccount() {
        var user = new IdentityAccount("Synthetic Role",
            "identity-contract@example.invalid", "unused-test-only-hash",
            Role.DEVELOPER);
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    private static JwtService rs256Issuer() {
        return new JwtService("", 300, ISSUER, AUDIENCE, "rs256",
            Base64.getEncoder().encodeToString(signingKey.getPrivate().getEncoded()),
            KID);
    }

    @Test
    void realRs256IssuerProducesUniqueSignedUuidV4ForEveryIssuedJwt() {
        // Genuine production issuer code, not a reimplemented test JWT signer.
        var issuer = rs256Issuer();
        String first = issuer.issue(syntheticAccount());
        String second = issuer.issue(syntheticAccount());
        assertNotEquals(first, second,
            "Distinct issuance must not reuse a JWT, including within one second");
        for (String token : new String[]{first, second}) {
            var signed = Jwts.parser().verifyWith(signingKey.getPublic())
                .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .build().parseSignedClaims(token);
            assertEquals("RS256", signed.getHeader().getAlgorithm());
            assertEquals(KID, signed.getHeader().getKeyId());
            var claims = signed.getPayload();
            assertEquals(42L, ((Number) claims.get("uid")).longValue());
            assertEquals("DEVELOPER", claims.get("role"));
            assertEquals("identity-contract@example.invalid", claims.getSubject());
            assertEquals(1, claims.getAudience().size());
            assertTrue(claims.getAudience().contains(AUDIENCE));
            Object signedScopes = claims.get("scopes");
            assertInstanceOf(java.util.Collection.class, signedScopes);
            assertTrue(((java.util.Collection<?>) signedScopes)
                .contains("orchestrator:read"),
                "Signed role/scopes must preserve real Identity permissions");
            assertEquals(300,
                claims.getExpiration().toInstant().getEpochSecond()
                  - claims.getIssuedAt().toInstant().getEpochSecond());
            String signedId = claims.getId();
            assertNotNull(signedId);
            assertEquals(36, signedId.length());
            assertEquals(signedId.toLowerCase(java.util.Locale.ROOT), signedId);
            UUID uuid = UUID.fromString(signedId);
            assertEquals(4, uuid.version(), "Random UUIDv4 is required");
            assertEquals(2, uuid.variant());
            assertEquals(uuid.toString(), signedId);
        }
        String firstId = Jwts.parser().verifyWith(signingKey.getPublic())
            .build().parseSignedClaims(first).getPayload().getId();
        String secondId = Jwts.parser().verifyWith(signingKey.getPublic())
            .build().parseSignedClaims(second).getPayload().getId();
        assertNotEquals(firstId, secondId, "Signed JTIs must be unique");
    }

    @Test
    void onlyTheMatchingPublicKeyIssuerAndAudienceValidateTheToken() {
        String signed = rs256Issuer().issue(syntheticAccount());
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(unrelatedKey.getPublic())
            .build().parseSignedClaims(signed));
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(signingKey.getPublic())
            .requireIssuer("untrusted-identity")
            .build().parseSignedClaims(signed));
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(signingKey.getPublic())
            .requireAudience("some-other-service")
            .build().parseSignedClaims(signed));
    }

    @Test
    void unchangedLegacyHmacIssuerCannotImpersonateRs256OrCreateSessionFact() {
        var legacy = new JwtService(LEGACY_KEY, 300, ISSUER, AUDIENCE);
        String issued = legacy.issue(syntheticAccount());
        var parsed = Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(LEGACY_KEY.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(issued);
        assertNotEquals("RS256", parsed.getHeader().getAlgorithm());
        assertNull(parsed.getPayload().getId(),
            "The compatibility change must be restricted to opt-in RS256 issuance");
        assertThrows(RuntimeException.class, () -> Jwts.parser()
            .verifyWith(signingKey.getPublic()).build().parseSignedClaims(issued));
    }
}
