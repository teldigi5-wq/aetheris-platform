package io.aetheris.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B62-G4 CI-ONLY integration test, NOT a Spring application component.
 * Performs genuine JJWT RS256 signature verification and a genuine read-only
 * PostgreSQL JDBC lookup in the *disposable b62g_synthetic* CI database.
 * This DOES NOT integrate Gateway, User, Audit or deployed Identity JWT filters.
 */
class B62G4RealJdbcRevocationIT {
    private static final String ISSUER = "b62g-identity";
    private static final String AUDIENCE = "aetheris-api";
    private static final String CURRENT = "key-current";
    private static final String COMPROMISED = "key-compromised";
    private static final Instant NOW = Instant.ofEpochSecond(12200L);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String JDBC_URL =
        "jdbc:postgresql://127.0.0.1:15479/b62g_synthetic"
        + "?connectTimeout=2&socketTimeout=3&sslmode=disable";
    private static final String SYNTHETIC_PASSWORD =
        "test-only-b62g-never-use-in-production";
    private static KeyPair current, compromised;

    @BeforeAll
    static void ephemeralKeys() throws Exception {
        assertEquals(JDBC_URL, System.getProperty("b62g4.jdbcUrl"),
            "Test-only loopback URL is mandatory; never accept arbitrary DB URLs");
        assertTrue(Set.of("online", "restarted", "outage")
            .contains(System.getProperty("b62g4.mode")),
            "Run this test only within the reviewed B62-G4 CI harness");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        current = generator.generateKeyPair();
        compromised = generator.generateKeyPair();
        assertNotEquals(current.getPublic(), compromised.getPublic());
    }

    @FunctionalInterface
    interface ReadonlyDecision {
        boolean allowed(long validatedUid, String validatedIssuer,
                        String verifiedKid, long verifiedIat) throws Exception;
    }

    static boolean verifyThenRead(String token, PublicKey pinnedPublicKey,
                                  String kid, ReadonlyDecision decision) {
        try {
            if (token == null || token.isBlank() || token.length() > 4096
                    || pinnedPublicKey == null || decision == null || kid == null) {
                return false;
            }
            Jws<Claims> signed = Jwts.parser()
                .clock(() -> Date.from(CLOCK.instant()))
                .clockSkewSeconds(0)
                .verifyWith(pinnedPublicKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build().parseSignedClaims(token);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !kid.equals(signed.getHeader().getKeyId())) {
                return false;
            }
            Claims claims = signed.getPayload();
            if (!Set.of(AUDIENCE).equals(claims.getAudience())
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || !"ADMIN".equals(claims.get("role", String.class))
                    || !(claims.get("scopes") instanceof List<?> scopes)
                    || !scopes.contains("users:read")) {
                return false;
            }
            Object uidValue = claims.get("uid");
            long uid;
            if (uidValue instanceof Integer i && i > 0) {
                uid = i.longValue();
            } else if (uidValue instanceof Long l && l > 0) {
                uid = l;
            } else {
                return false;
            }
            Date issued = claims.getIssuedAt(), expires = claims.getExpiration();
            if (issued == null || expires == null
                    || !expires.toInstant().isAfter(issued.toInstant())
                    || Duration.between(issued.toInstant(), expires.toInstant())
                        .compareTo(Duration.ofSeconds(3600)) > 0
                    || issued.toInstant().isAfter(NOW.plusSeconds(30))
                    || !expires.toInstant().isAfter(NOW)) {
                return false;
            }
            // No raw bearer, HTTP headers or unverified claims reach SQL.
            return decision.allowed(uid, ISSUER, kid, issued.toInstant().getEpochSecond());
        } catch (Exception malformedOrUnavailable) {
            return false;
        }
    }

    static boolean readOnlyJdbcDecision(long uid, String issuer,
                                         String kid, long iat) {
        if (uid < 1 || iat < 1 || !ISSUER.equals(issuer)
                || !Set.of(CURRENT, COMPROMISED).contains(kid)) {
            return false;
        }
        // Each lookup uses a new connection, an explicit read-only transaction
        // and an ephemeral NOLOGIN verifier role. No cached ALLOW decisions.
        // CI superuser proxy is SYNTHETIC ONLY; production service identity
        // and secret custody require a separate security-reviewed design.
        try (Connection connection = DriverManager.getConnection(
                JDBC_URL, "b62g_synthetic", SYNTHETIC_PASSWORD)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement role = connection.createStatement()) {
                role.setQueryTimeout(2);
                role.execute("SET LOCAL ROLE b62g_verifier");
            }
            try (PreparedStatement query = connection.prepareStatement(
                "SELECT b62g_proof.synthetic_post_signature_allowed(?,?,?,?)")) {
                query.setQueryTimeout(2);
                query.setLong(1, uid);
                query.setString(2, issuer);
                query.setString(3, kid);
                query.setLong(4, iat);
                try (ResultSet result = query.executeQuery()) {
                    boolean allowed = result.next() && result.getBoolean(1)
                        && !result.wasNull() && !result.next();
                    connection.rollback();
                    return allowed;
                }
            }
        } catch (SQLException orNetworkFailure) {
            return false;
        }
    }

    static String token(KeyPair signer, String kid, long uid, long issuedAt,
                        String audience) {
        return Jwts.builder()
            .issuer(ISSUER).audience().add(audience).and()
            .subject("b62g4-synthetic@example.invalid")
            .claim("uid", uid).claim("role", "ADMIN")
            .claim("scopes", Role.ADMIN.scopes().stream().sorted().toList())
            .issuedAt(Date.from(Instant.ofEpochSecond(issuedAt)))
            .expiration(Date.from(NOW.plusSeconds(450)))
            .header().keyId(kid).and()
            .signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    @Test
    void verifiedJavaRs256MustObeyActualReadonlyPostgresAndOutage() {
        String mode = System.getProperty("b62g4.mode");
        boolean live = !"outage".equals(mode);
        record Case(String reason, String jwt, PublicKey pinnedKey,
                    String kid, boolean allowedOnline) {}
        List<Case> cases = List.of(
            new Case("fresh-uid-42", token(current, CURRENT, 42, 12001, AUDIENCE),
                     current.getPublic(), CURRENT, true),
            new Case("cutoff-uid-42", token(current, CURRENT, 42, 12000, AUDIENCE),
                     current.getPublic(), CURRENT, false),
            new Case("older-uid-42", token(current, CURRENT, 42, 11999, AUDIENCE),
                     current.getPublic(), CURRENT, false),
            new Case("unrelated-uid-43", token(current, CURRENT, 43, 11999, AUDIENCE),
                     current.getPublic(), CURRENT, true),
            new Case("blocked-signer", token(compromised, COMPROMISED, 43, 12001, AUDIENCE),
                     compromised.getPublic(), COMPROMISED, false),
            new Case("foreign-audience", token(current, CURRENT, 42, 12001, "aetheris-runtime"),
                     current.getPublic(), CURRENT, false),
            new Case("wrong-key", token(compromised, CURRENT, 42, 12001, AUDIENCE),
                     current.getPublic(), CURRENT, false),
            new Case("wrong-kid", token(current, COMPROMISED, 42, 12001, AUDIENCE),
                     current.getPublic(), CURRENT, false),
            new Case("invalid-uid", token(current, CURRENT, 0, 12001, AUDIENCE),
                     current.getPublic(), CURRENT, false));
        AtomicInteger reads = new AtomicInteger();
        for (Case item : cases) {
            boolean actual = verifyThenRead(item.jwt, item.pinnedKey, item.kid,
                (uid, iss, verifiedKid, iat) -> {
                    reads.incrementAndGet();
                    return readOnlyJdbcDecision(uid, iss, verifiedKid, iat);
                });
            assertEquals(live && item.allowedOnline, actual,
                mode + " decision failed: " + item.reason);
        }
        // Only five validly signed/authorized cases reach JDBC; wrong
        // audience/key/kid/UID never consume a database connection.
        assertEquals(5, reads.get(), "Signed claim admission must precede SQL");
        System.out.println("B62G4_" + mode.toUpperCase()
            + "_REAL_JAVA_JDBC_SIGNED_DECISIONS_PASSED cases=" + cases.size());
    }

    @Test
    void explicitTimeoutAndFailureReturnDenialWithoutAnyBypass() {
        String jwt = token(current, CURRENT, 42, 12001, AUDIENCE);
        assertFalse(verifyThenRead(jwt, current.getPublic(), CURRENT,
            (uid, issuer, kid, iat) -> {
                throw new java.sql.SQLTimeoutException("synthetic bounded timeout");
            }));
        assertFalse(verifyThenRead(jwt, current.getPublic(), CURRENT,
            (uid, issuer, kid, iat) -> false));
    }
}
