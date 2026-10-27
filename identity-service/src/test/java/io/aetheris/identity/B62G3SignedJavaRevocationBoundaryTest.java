package io.aetheris.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B62-G3 CI-ONLY proof. This class is in test source, NOT a production filter,
 * Spring component, deployed revocation client or database migration.
 * RSA private keys are generated in the test process and never exported.
 *
 * B62-G2 independently proves test-only PostgreSQL persistence and role ACLs.
 * This slice uses actual JJWT RS256 verification and a synthetic post-signature
 * decision boundary. Real Gateway, User and Audit still have NO revocation hook.
 */
class B62G3SignedJavaRevocationBoundaryTest {
    private static final String ISSUER = JwtService.DEFAULT_ISSUER;
    private static final String AUDIENCE = JwtService.DEFAULT_AUDIENCE;
    private static final String KID = "b62g3-ci-current";
    private static final String PREVIOUS = "b62g3-ci-previous";
    private static final Instant NOW = Instant.parse("2026-10-27T07:00:00Z");
    private static final Clock TEST_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static KeyPair current;
    private static KeyPair previous;

    @BeforeAll
    static void createOnlyEphemeral3072BitRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        current = generator.generateKeyPair();
        previous = generator.generateKeyPair();
        assertNotEquals(current.getPublic(), previous.getPublic());
    }

    @FunctionalInterface
    interface ReadonlyRevocationDecision {
        boolean allowed(long signedUid, String signedIssuer,
                        String verifiedKid, long signedIatEpoch) throws Exception;
    }

    /**
     * B62-G3 is a reference boundary, NOT wired to any production request.
     * JJWT verifies RS256 before any revocation read. On failure: DENY.
     */
    private static boolean signedThenRevocationLookup(
            String token, PublicKey pinnedPublicKey, String pinnedKid,
            Clock clock, ReadonlyRevocationDecision reader) {
        try {
            if (token == null || token.length() > 4096 || token.isBlank()
                    || pinnedPublicKey == null || pinnedKid == null
                    || reader == null || clock == null) {
                return false;
            }
            Jws<Claims> verified = Jwts.parser()
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(0)
                    .verifyWith(pinnedPublicKey)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .build().parseSignedClaims(token);
            if (!"RS256".equals(verified.getHeader().getAlgorithm())
                    || !pinnedKid.equals(verified.getHeader().getKeyId())) {
                return false;
            }
            Claims claims = verified.getPayload();
            if (!Set.of(AUDIENCE).equals(claims.getAudience())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || !"ADMIN".equals(claims.get("role", String.class))) {
                return false;
            }
            Object uidClaim = claims.get("uid");
            long uid;
            if (uidClaim instanceof Integer integer && integer > 0) {
                uid = integer.longValue();
            } else if (uidClaim instanceof Long longer && longer > 0) {
                uid = longer;
            } else {
                return false;
            }
            if (!(claims.get("scopes") instanceof List<?> scopes)
                    || !scopes.contains("users:read")) {
                return false;
            }
            Date issued = claims.getIssuedAt();
            Date expires = claims.getExpiration();
            Instant now = clock.instant();
            if (issued == null || expires == null
                    || !expires.toInstant().isAfter(issued.toInstant())
                    || Duration.between(issued.toInstant(), expires.toInstant())
                            .compareTo(Duration.ofSeconds(3600)) > 0
                    || issued.toInstant().isAfter(now.plusSeconds(30))
                    || !expires.toInstant().isAfter(now)) {
                return false;
            }
            // Only verified signed claims enter the trusted lookup; no JWT,
            // caller headers, resource identity or SQL is forwarded.
            return reader.allowed(uid, ISSUER, pinnedKid,
                    issued.toInstant().getEpochSecond());
        } catch (Exception invalidSignatureClaimsOrLookupFailure) {
            return false;
        }
    }

    private static String signed(KeyPair signer, String kid, String issuer,
                                 String audience, Object uid, String role,
                                 Instant issued, Instant notBefore, Instant expires) {
        var builder = Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("b62g3-synthetic@example.invalid")
                .claim("uid", uid)
                .claim("role", role)
                .claim("scopes", Role.ADMIN.scopes().stream().sorted().toList())
                .issuedAt(Date.from(issued))
                .expiration(Date.from(expires));
        if (notBefore != null) {
            builder.notBefore(Date.from(notBefore));
        }
        return builder.header().keyId(kid).and()
                .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String valid() {
        return signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                NOW.minusSeconds(10), null, NOW.plusSeconds(600));
    }

    @Test
    void genuineSignatureAndExactSignedClaimsPrecedeAnyLookup() {
        AtomicInteger calls = new AtomicInteger();
        boolean accepted = signedThenRevocationLookup(valid(), current.getPublic(),
                KID, TEST_CLOCK, (uid, issuer, kid, iat) -> {
                    calls.incrementAndGet();
                    assertEquals(42L, uid);
                    assertEquals(ISSUER, issuer);
                    assertEquals(KID, kid);
                    assertEquals(NOW.minusSeconds(10).getEpochSecond(), iat);
                    return true;
                });
        assertTrue(accepted);
        assertEquals(1, calls.get());
    }

    @Test
    void invalidCredentialsAreRejectedBeforeRevocationLookup() {
        String token = valid();
        String[] parts = token.split("\\.");
        String signature = parts[2];
        String tampered = parts[0] + "." + parts[1] + "."
                + (signature.startsWith("A") ? "B" : "A") + signature.substring(1);
        String dualAudience = Jwts.builder()
                .issuer(ISSUER).audience().add(AUDIENCE).add("aetheris-runtime").and()
                .subject("b62g3-synthetic@example.invalid")
                .claim("uid", 42L).claim("role", "ADMIN")
                .claim("scopes", Role.ADMIN.scopes().stream().sorted().toList())
                .issuedAt(Date.from(NOW.minusSeconds(10)))
                .expiration(Date.from(NOW.plusSeconds(600)))
                .header().keyId(KID).and()
                .signWith(current.getPrivate(), Jwts.SIG.RS256).compact();
        List<String> invalid = List.of(
                dualAudience,
                tampered,
                signed(previous, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, PREVIOUS, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, "foreign-issuer", AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, "aetheris-runtime", 42L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, AUDIENCE, 0L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, AUDIENCE, 42.5, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, AUDIENCE, 42L, "API_CONSUMER",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.plusSeconds(31), null, NOW.plusSeconds(632)),
                signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(10), NOW.plusSeconds(1), NOW.plusSeconds(600)),
                signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(610), null, NOW),
                signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                        NOW.minusSeconds(10), null, NOW.plusSeconds(3601)),
                "not.a.jwt");
        AtomicInteger calls = new AtomicInteger();
        for (String bad : invalid) {
            assertFalse(signedThenRevocationLookup(bad, current.getPublic(),
                    KID, TEST_CLOCK, (uid, issuer, kid, iat) -> {
                        calls.incrementAndGet();
                        return true;
                    }), "Invalid signed token reached an allow decision");
        }
        assertEquals(0, calls.get(),
                "No invalid token is permitted to reach the database decision");
    }

    @Test
    void threeSimulatedConsumersEnforceMonotonicPrincipalAndKidDenial() {
        AtomicLong cutoff = new AtomicLong(0L);
        AtomicBoolean blockedKid = new AtomicBoolean(false);
        ReadonlyRevocationDecision store = (uid, issuer, kid, iat) ->
                uid == 42L && ISSUER.equals(issuer) && KID.equals(kid)
                        && !blockedKid.get() && iat > cutoff.get();
        String token = valid();
        // Labels model three independent consumers. Actual service filters
        // are NOT wired to this reference implementation.
        for (String consumer : List.of("gateway-sim", "user-sim", "audit-sim")) {
            assertTrue(signedThenRevocationLookup(token, current.getPublic(),
                    KID, TEST_CLOCK, store), consumer + " before revocation");
        }
        // One monotonic emergency cutoff is seen by every simulated reader.
        cutoff.accumulateAndGet(NOW.minusSeconds(10).getEpochSecond(), Math::max);
        for (String consumer : List.of("gateway-sim", "user-sim", "audit-sim")) {
            assertFalse(signedThenRevocationLookup(token, current.getPublic(),
                    KID, TEST_CLOCK, store), consumer + " after revocation");
        }
        // A newly issued token is not blocked by the earlier account epoch,
        // but emergency signer block must then independently deny it.
        String fresh = signed(current, KID, ISSUER, AUDIENCE, 42L, "ADMIN",
                NOW.minusSeconds(5), null, NOW.plusSeconds(600));
        assertTrue(signedThenRevocationLookup(fresh, current.getPublic(),
                KID, TEST_CLOCK, store));
        blockedKid.set(true);
        assertFalse(signedThenRevocationLookup(fresh, current.getPublic(),
                KID, TEST_CLOCK, store));
    }

    @Test
    void lookupErrorTimeoutAndExplicitDenialAllFailClosed() {
        assertFalse(signedThenRevocationLookup(valid(), current.getPublic(),
                KID, TEST_CLOCK, (uid, issuer, kid, iat) -> false));
        assertFalse(signedThenRevocationLookup(valid(), current.getPublic(),
                KID, TEST_CLOCK, (uid, issuer, kid, iat) -> {
                    throw new java.util.concurrent.TimeoutException("test-only timeout");
                }));
        assertFalse(signedThenRevocationLookup(valid(), current.getPublic(),
                KID, TEST_CLOCK, (uid, issuer, kid, iat) -> {
                    throw new java.io.IOException("test-only datastore unavailable");
                }));
        assertFalse(signedThenRevocationLookup(valid(), current.getPublic(),
                KID, TEST_CLOCK, null));
    }
}
