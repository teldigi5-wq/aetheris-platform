package io.aetheris.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Actual JwtService restricted target issuance; no HTTP/token exchange enabled. */
class ApprovedAudienceTokenIssuanceTest {
    private static final String SECRET =
        "SYNTHETIC_INTERNAL_TARGET_ISSUANCE_SECRET_NOT_DEPLOYABLE_20261027";
    private static final String ISSUER = "https://identity.aetheris.example.invalid";

    private IdentityAccount account(Role role, long uid) {
        IdentityAccount value = new IdentityAccount(
            "Synthetic", "scope-test@example.invalid", "not-password", role);
        ReflectionTestUtils.setField(value, "id", uid);
        return value;
    }

    private Claims verified(String jwt) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(jwt).getPayload();
    }

    private JwtService enabled() {
        return new JwtService(SECRET, 3600, ISSUER, "aetheris-user-api", true);
    }

    @Test
    void fixedApprovedTargetsYieldDistinctSignedRecipientsAndStrictScopedClaims() {
        JwtService issuer = enabled();
        Set<String> distinct = new HashSet<>();
        for (ApprovedTokenAudience target : ApprovedTokenAudience.values()) {
            Claims claims = verified(
                issuer.issueForApprovedAudience(account(Role.ADMIN, 42), target));
            assertTrue(distinct.add(target.audience()));
            assertEquals(Set.of(target.audience()), claims.getAudience());
            assertEquals(ISSUER, claims.getIssuer());
            assertEquals(42L, ((Number) claims.get("uid")).longValue());
            assertEquals("ADMIN", claims.get("role", String.class));
            assertEquals(target.allowedScopes(Role.ADMIN),
                Set.copyOf(claims.get("scopes", java.util.List.class)));
            long age = claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond();
            assertTrue(age > 0 && age <= 300, "Target token must live at most five minutes");
            assertNull(claims.get("tenant_id"), "Do not forge or infer tenancy");
        }
        assertEquals(3, distinct.size());
    }

    @Test
    void developerGetsOnlyItsOriginalIntersectedScopes() {
        JwtService issuer = enabled();
        for (ApprovedTokenAudience target : ApprovedTokenAudience.values()) {
            Claims claims = verified(
                issuer.issueForApprovedAudience(account(Role.DEVELOPER, 7), target));
            Set<String> issued = new HashSet<>(
                claims.get("scopes", java.util.List.class));
            assertEquals(target.allowedScopes(Role.DEVELOPER), issued);
            assertTrue(Role.DEVELOPER.scopes().containsAll(issued));
            assertFalse(issued.contains("identity:write"));
            assertFalse(issued.contains("services:write"));
        }
    }

    @Test
    void consumerAndMissingIdentityAreRefusedBeforeMinting() {
        JwtService issuer = enabled();
        for (ApprovedTokenAudience target : ApprovedTokenAudience.values()) {
            assertThrows(SecurityException.class,
                () -> issuer.issueForApprovedAudience(account(Role.API_CONSUMER, 42), target));
        }
        assertThrows(IllegalArgumentException.class,
            () -> issuer.issueForApprovedAudience(account(Role.ADMIN, 0),
                ApprovedTokenAudience.USER));
        assertThrows(IllegalArgumentException.class,
            () -> issuer.issueForApprovedAudience(null, ApprovedTokenAudience.USER));
        assertThrows(IllegalArgumentException.class,
            () -> issuer.issueForApprovedAudience(account(Role.ADMIN, 42), null));
    }

    @Test
    void mintingIsDisabledByDefaultAndRejectsUnpairedOperatorSettings() {
        JwtService legacy = new JwtService(SECRET, 3600);
        assertThrows(IllegalStateException.class,
            () -> legacy.issueForApprovedAudience(account(Role.ADMIN, 42),
                ApprovedTokenAudience.USER));
        JwtService targetedButNotApproved =
            new JwtService(SECRET, 3600, ISSUER, "aetheris-user-api");
        assertThrows(IllegalStateException.class,
            () -> targetedButNotApproved.issueForApprovedAudience(
                account(Role.ADMIN, 42), ApprovedTokenAudience.USER));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, "", "", true));
        assertThrows(IllegalArgumentException.class,
            () -> new JwtService(SECRET, 3600, ISSUER, "", true));
    }

    @Test
    void shorterConfiguredTtlIsNotExtended() {
        JwtService issuer = new JwtService(
            SECRET, 45, ISSUER, "aetheris-user-api", true);
        Claims claims = verified(issuer.issueForApprovedAudience(
            account(Role.ADMIN, 42), ApprovedTokenAudience.USER));
        long age = claims.getExpiration().toInstant().getEpochSecond()
            - claims.getIssuedAt().toInstant().getEpochSecond();
        assertTrue(age > 0 && age <= 45);
    }
}
