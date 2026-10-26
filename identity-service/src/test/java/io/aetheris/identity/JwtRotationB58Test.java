package io.aetheris.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B58 SYNTHETIC REHEARSAL ONLY. Does not activate production key rotation.
 *
 * Uses the actual JJWT 0.12 signing and signature-verification engine.
 * A test-only candidate list is bounded to two independently configured keys.
 * NEVER trust a decoded but unverified JWT kid as identity or authorization.
 *
 * Production B57 currently signs/validates one shared HMAC secret without
 * kid-based key resolution. Passing these tests is NOT proof of a deployed
 * trust store, JWKS endpoint, service-to-service identity, or revocation.
 */
class JwtRotationB58Test {
    private static final String ISSUER = "aetheris-identity";
    private static final String API_AUDIENCE = "aetheris-api";
    private static final String RUNTIME_AUDIENCE = "aetheris-runtime";
    private static final String OLD_KID = "synthetic-epoch-a";
    private static final String NEW_KID = "synthetic-epoch-b";
    private static final int MAX_LIFETIME_SECONDS = 300;

    private static SecretKey syntheticKey(String epoch) {
        // Test-only public seed; NOT a credential and never used by production.
        String seed = ("only-synthetic-B58-JJWT-rotation-" + epoch + "-").repeat(3);
        return Keys.hmacShaKeyFor(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static final SecretKey OLD = syntheticKey("old");
    private static final SecretKey NEW = syntheticKey("new");
    private static final SecretKey UNAPPROVED = syntheticKey("unapproved");

    private static String mint(String kid, SecretKey key, String issuer, Set<String> audiences,
                               Instant issued, Instant expires, boolean hs512) {
        var builder = Jwts.builder();
        if (kid != null) builder.header().keyId(kid).and();
        builder.issuer(issuer);
        if (audiences != null) {
            for (String audience : audiences) {
                builder.audience().add(audience).and();
            }
        }
        builder.subject("synthetic-user@example.invalid")
            .claim("uid", 42L)
            .claim("role", "ADMIN")
            .claim("scopes", Set.of("users:read", "services:read"));
        if (issued != null) builder.issuedAt(Date.from(issued));
        if (expires != null) builder.expiration(Date.from(expires));
        return builder.signWith(key, hs512 ? Jwts.SIG.HS512 : Jwts.SIG.HS256).compact();
    }

    private static String mintCurrent(String kid, SecretKey key) {
        Instant now = Instant.now();
        return mint(kid, key, ISSUER, Set.of(API_AUDIENCE),
            now.minusSeconds(5), now.plusSeconds(120), false);
    }

    /**
     * Test-only bounded verifier models rollout before -> overlap -> after.
     * Verification must succeed with a trusted candidate secret FIRST,
     * then validate the signed token's kid and all identity/lifetime claims.
     * This does not parse untrusted header JSON to load a key or fetch a URL.
     */
    private static Claims verified(String jwt, Map<String, SecretKey> trusted, String audience) {
        if (jwt == null || jwt.length() > 4096 || jwt.split("\\.", -1).length != 3
                || audience == null || audience.isBlank() || trusted == null
                || trusted.isEmpty() || trusted.size() > 2) {
            throw new SecurityException("B58_DENY_UNTRUSTED_ROTATION");
        }
        for (var key : trusted.entrySet()) {
            if (!key.getKey().matches("[a-z][a-z0-9-]{0,30}")
                    || key.getValue() == null || key.getValue().getEncoded().length < 32) {
                throw new SecurityException("B58_DENY_INVALID_KEYSET");
            }
        }
        for (var entry : trusted.entrySet()) {
            try {
                var signed = Jwts.parser()
                    .verifyWith(entry.getValue())
                    .requireIssuer(ISSUER)
                    .requireAudience(audience)
                    .build().parseSignedClaims(jwt);
                var header = signed.getHeader();
                Claims claims = signed.getPayload();
                Instant now = Instant.now();
                Date issued = claims.getIssuedAt(), expires = claims.getExpiration();
                // No kidless legacy fallback and no confused-deputy audience overlap.
                if (!entry.getKey().equals(header.getKeyId())
                        || !"HS256".equals(header.getAlgorithm())
                        || claims.getAudience() == null
                        || !Set.of(audience).equals(claims.getAudience())
                        || issued == null || expires == null
                        || issued.toInstant().isAfter(now.plusSeconds(30))
                        || !expires.toInstant().isAfter(now)
                        || !expires.toInstant().isAfter(issued.toInstant())
                        || expires.toInstant().isAfter(issued.toInstant().plusSeconds(MAX_LIFETIME_SECONDS))
                        || !("synthetic-user@example.invalid").equals(claims.getSubject())) {
                    continue;
                }
                return claims;
            } catch (RuntimeException invalidCandidate) {
                // Never emit secret, token, or untrusted kid to CI logs.
            }
        }
        throw new SecurityException("B58_DENY_UNTRUSTED_ROTATION");
    }

    @Test
    void oldKeyWorksBeforeCutoverAndNewKeyCannotBeUsedPrematurely() {
        String oldToken = mintCurrent(OLD_KID, OLD);
        String newToken = mintCurrent(NEW_KID, NEW);
        assertEquals(42L, ((Number) verified(oldToken, Map.of(OLD_KID, OLD), API_AUDIENCE).get("uid")).longValue());
        assertThrows(SecurityException.class,
            () -> verified(newToken, Map.of(OLD_KID, OLD), API_AUDIENCE));
    }

    @Test
    void boundedOverlapAcceptsOnlyBothExplicitlyInstalledEpochs() {
        var overlap = Map.of(OLD_KID, OLD, NEW_KID, NEW);
        assertNotNull(verified(mintCurrent(OLD_KID, OLD), overlap, API_AUDIENCE));
        assertNotNull(verified(mintCurrent(NEW_KID, NEW), overlap, API_AUDIENCE));
    }

    @Test
    void retiredOldKeyImmediatelyDeniedAfterOverlapWindow() {
        String prior = mintCurrent(OLD_KID, OLD);
        var after = Map.of(NEW_KID, NEW);
        assertThrows(SecurityException.class, () -> verified(prior, after, API_AUDIENCE));
        assertNotNull(verified(mintCurrent(NEW_KID, NEW), after, API_AUDIENCE));
    }

    @Test
    void correctKidWithUnapprovedSignatureCannotAuthenticate() {
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, UNAPPROVED), Map.of(NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void signedKidLabelMustMatchTheActualTrustedVerifyingKey() {
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, OLD), Map.of(OLD_KID, OLD, NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void legacyKidlessEvenWithLegitimateKeyIsNeverAutomaticallyAccepted() {
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(null, OLD), Map.of(OLD_KID, OLD), API_AUDIENCE));
    }

    @Test
    void otherIssuerRejectedWithCorrectKeyAndKid() {
        Instant now = Instant.now();
        String token = mint(NEW_KID, NEW, "other-issuer", Set.of(API_AUDIENCE),
            now.minusSeconds(5), now.plusSeconds(120), false);
        assertThrows(SecurityException.class, () -> verified(token, Map.of(NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void workloadTokenMustNotBecomeAPlatformApiCredential() {
        Instant now = Instant.now();
        String worker = mint(NEW_KID, NEW, ISSUER, Set.of(RUNTIME_AUDIENCE),
            now.minusSeconds(5), now.plusSeconds(120), false);
        assertThrows(SecurityException.class,
            () -> verified(worker, Map.of(NEW_KID, NEW), API_AUDIENCE));
        // A distinct verifier would require additional workload-specific claims.
        assertNotNull(verified(worker, Map.of(NEW_KID, NEW), RUNTIME_AUDIENCE));
    }

    @Test
    void ambiguousMultiAudienceTokenIsDeniedEvenWhenApiAudiencePresent() {
        Instant now = Instant.now();
        String dual = mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE, RUNTIME_AUDIENCE),
            now.minusSeconds(5), now.plusSeconds(120), false);
        assertThrows(SecurityException.class,
            () -> verified(dual, Map.of(NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void expiredAndFutureIssuedTokenAreDenied() {
        Instant now = Instant.now();
        String expired = mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE),
            now.minusSeconds(240), now.minusSeconds(30), false);
        String future = mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE),
            now.plusSeconds(90), now.plusSeconds(180), false);
        var onlyNew = Map.of(NEW_KID, NEW);
        assertThrows(SecurityException.class, () -> verified(expired, onlyNew, API_AUDIENCE));
        assertThrows(SecurityException.class, () -> verified(future, onlyNew, API_AUDIENCE));
    }

    @Test
    void unboundedOrMissingLifetimeIsDenied() {
        Instant now = Instant.now();
        String tooLong = mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE),
            now.minusSeconds(1), now.plusSeconds(3600), false);
        String missingIat = mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE),
            null, now.plusSeconds(120), false);
        var onlyNew = Map.of(NEW_KID, NEW);
        assertThrows(SecurityException.class, () -> verified(tooLong, onlyNew, API_AUDIENCE));
        assertThrows(SecurityException.class, () -> verified(missingIat, onlyNew, API_AUDIENCE));
    }

    @Test
    void rejectsOtherHmacAlgorithmEvenWithMatchingKey() {
        assertThrows(SecurityException.class, () -> verified(
            mint(NEW_KID, NEW, ISSUER, Set.of(API_AUDIENCE),
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(120), true),
            Map.of(NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void rejectsThreeOrMoreSimultaneouslyTrustedKeysAndMalformedTokens() {
        var tooMany = Map.of(OLD_KID, OLD, NEW_KID, NEW, "synthetic-epoch-c", UNAPPROVED);
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, NEW), tooMany, API_AUDIENCE));
        assertThrows(SecurityException.class,
            () -> verified("not-a-jwt", Map.of(NEW_KID, NEW), API_AUDIENCE));
    }

    @Test
    void invalidTrustStoreAndServiceAudienceConfigurationAreDenied() {
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, NEW), Map.of(), API_AUDIENCE));
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, NEW), Map.of(NEW_KID, NEW), ""));
        assertThrows(SecurityException.class,
            () -> verified(mintCurrent(NEW_KID, NEW), Map.of("INVALID KID", NEW), API_AUDIENCE));
    }
}
