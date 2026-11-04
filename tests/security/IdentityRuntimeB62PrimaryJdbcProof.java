package io.aetheris.identity;

import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * REVIEW-ONLY: genuine Platform Identity-issued RS256 JWT -> immutable Runtime
 * verifier -> Platform B62-G1 function through a separately authenticated,
 * read-only B62-G8 PostgreSQL role. Emergency mutations are made ONLY through
 * B62-G13's separately authenticated, audited SECURITY DEFINER functions.
 *
 * Source contract: Runtime a772cb803539c982e6ecc7e9faa7b62a1a4a19cc,
 * B62-G1/G2/G8/G13 SQL pinned and installed in ephemeral PostgreSQL16 by CI.
 * This is NOT production revocation, a session allowlist, an HTTP/SSE filter,
 * an authorization decision, mTLS, multi-node consistency, or an approved
 * migration. Synthetic roles/credentials MUST NEVER be used in deployment.
 */
public final class IdentityRuntimeB62PrimaryJdbcProof {
    private static final String ISS = "aetheris-identity";
    private static final String AUD = "aetheris-orchestrator-api";
    private static final String KID = "b62-crossrepo-ci-a";
    private static final String URL =
        "jdbc:postgresql://127.0.0.1:55432/b62g_synthetic"
        + "?connectTimeout=2&socketTimeout=3&sslmode=disable";
    private static final String BAD_NETWORK_URL =
        "jdbc:postgresql://127.0.0.1:55433/b62g_synthetic"
        + "?connectTimeout=1&socketTimeout=2&sslmode=disable";
    private static final String READER = "b62g8_user_reader";
    private static final String WRITER = "b62g13_emergency_writer";
    private static final String AUDITOR = "b62g13_audit_reviewer";

    private IdentityRuntimeB62PrimaryJdbcProof() {}

    private static void check(boolean b, String reason) {
        if (!b) throw new AssertionError(reason);
    }

    private static void isDenied(ThrowingChecked task, String reason) throws Exception {
        try {
            task.run();
        } catch (SQLException denied) {
            check("42501".equals(denied.getSQLState()),
                "Expected actual PostgreSQL SQLSTATE 42501 for " + reason);
            return;
        }
        throw new AssertionError("Privilege escalation: " + reason);
    }

    @FunctionalInterface
    private interface ThrowingChecked {
        void run() throws Exception;
    }

    private static String fixturePassword(String key) {
        String value = System.getenv(key);
        check(value != null && !value.isBlank(),
            "Missing synthetic CI-only password: " + key);
        return value;
    }

    private static Connection login(String role, String secret) throws SQLException {
        Connection c = DriverManager.getConnection(URL, role, secret);
        try (var s = c.prepareStatement("SELECT current_user");
             var r = s.executeQuery()) {
            if (!r.next() || !role.equals(r.getString(1))) {
                c.close();
                throw new IllegalStateException("PostgreSQL authenticated wrong role");
            }
        }
        return c;
    }

    private static IdentityAccount syntheticAccount(long uid) throws Exception {
        IdentityAccount a = new IdentityAccount(
            "Synthetic", "b62-contract-" + uid + "@example.invalid",
            "never-a-real-password", Role.DEVELOPER);
        Field id = IdentityAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(a, uid);
        check(a.getId().equals(uid), "Unexpected synthetic UID fixture");
        return a;
    }

    private static String pem(KeyPair key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                 .encodeToString(key.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    /**
     * This says only "not blocked by the prototype's emergency cutoff".
     * It MUST NEVER be interpreted as an authenticated live session, tenant
     * membership, task authorization, or permission to enable any HTTP route.
     * Every call re-verifies the real Identity signature and re-queries the
     * ONE synthetic primary using a fresh TCP reader login (no cache/replica).
     */
    private static boolean notEmergencyBlocked(
            RuntimePinnedPrincipalVerifier runtime, String token,
            String jdbcUrl, String readerPassword) {
        try {
            var signed = runtime.verifyBearerForRevocation("Bearer " + token)
                .requireRevocationFacts();
            try (Connection c = DriverManager.getConnection(
                    jdbcUrl, READER, readerPassword)) {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                c.setReadOnly(true);
                c.setAutoCommit(false);
                boolean result = checkReadOnlyStatement(c,
                    signed.signedIdentityUid(), signed.verifiedIssuer(),
                    signed.verifiedKeyId(),
                    signed.signedIssuedAt().getEpochSecond());
                c.rollback();
                return result;
            }
        } catch (RuntimeException | SQLException absentOrInvalid) {
            // No fail-open, cached true result, stale fallback, or token leak.
            return false;
        }
    }

    private static boolean checkReadOnlyStatement(Connection c,
            long uid, String issuer, String kid, long signedIat) {
        try {
            if (!c.isReadOnly() || c.getAutoCommit()) return false;
            try (PreparedStatement q = c.prepareStatement("""
                SELECT current_user = 'b62g8_user_reader'
                   AND current_setting('transaction_isolation') = 'read committed'
                   AND current_setting('transaction_read_only') = 'on'
                   AND b62g_proof.synthetic_post_signature_allowed(?, ?, ?, ?)
                """)) {
                q.setLong(1, uid);
                q.setString(2, issuer);
                q.setString(3, kid);
                q.setLong(4, signedIat);
                try (var result = q.executeQuery()) {
                    return result.next() && result.getBoolean(1);
                }
            }
        } catch (SQLException | RuntimeException unavailable) {
            return false;
        }
    }

    private static void assertBadPasswordRejected(String correctReaderPassword)
            throws Exception {
        try (Connection ignored = DriverManager.getConnection(URL,
                READER, correctReaderPassword + "-incorrect")) {
            throw new AssertionError("Cross-container password auth failed open");
        } catch (SQLException rejected) {
            check("28P01".equals(rejected.getSQLState()),
                "Expected PostgreSQL invalid_password 28P01, not local trust");
        }
    }

    private static int countAudit(String auditorPassword) throws Exception {
        try (Connection c = login(AUDITOR, auditorPassword);
             var q = c.prepareStatement("""
                 SELECT count(*) FROM b62g_proof.b62g13_emergency_audit
                 """);
             var rows = q.executeQuery()) {
            check(rows.next(), "Audit count missing");
            return rows.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 0, "No user args or JDBC endpoint overrides");
        check("true".equals(System.getenv("GITHUB_ACTIONS"))
                && "yes".equals(System.getenv("B62_CROSS_REPO_CI_ONLY"))
                && "127.0.0.1".equals(System.getenv("PGHOST"))
                && "b62g_synthetic".equals(System.getenv("PGDATABASE")),
            "Only deliberately opted-in disposable GitHub PostgreSQL CI allowed");

        String readerPassword = fixturePassword("B62_CI_READER_PASSWORD");
        String writerPassword = fixturePassword("B62_CI_WRITER_PASSWORD");
        String auditorPassword = fixturePassword("B62_CI_AUDITOR_PASSWORD");

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair key = generator.generateKeyPair();
        var identity = new JwtService("", 300, ISS, AUD, "rs256",
            Base64.getEncoder().encodeToString(key.getPrivate().getEncoded()),
            KID);
        var runtime = new RuntimePinnedPrincipalVerifier(
            pem(key), KID, ISS, AUD, Clock.systemUTC());

        // Genuine Platform issuer tokens; never create signed JWT test strings.
        String token42a = identity.issue(syntheticAccount(42L));
        String token42b = identity.issue(syntheticAccount(42L));
        String token77 = identity.issue(syntheticAccount(77L));
        var signed42 = runtime.verifyBearerForRevocation("Bearer " + token42a)
            .requireRevocationFacts();
        var signed42b = runtime.verifyBearerForRevocation("Bearer " + token42b)
            .requireRevocationFacts();
        check(!signed42.signedTokenId().equals(signed42b.signedTokenId()),
            "Identity reused signed JTI");
        check(UUID.fromString(signed42.signedTokenId()).version() == 4,
            "Real Identity-issued JTI must be UUIDv4");
        check(signed42.signedIdentityUid() == 42L,
            "Actual Runtime verifier must authenticate signed UID");

        assertBadPasswordRejected(readerPassword);
        try (Connection reader = login(READER, readerPassword);
             var stmt = reader.createStatement()) {
            isDenied(() -> stmt.executeUpdate(
                "UPDATE b62g_proof.principal_epochs"
                + " SET reject_issued_at_or_before_epoch=1 WHERE principal_id=42"),
                "read-only verifier attempted direct emergency mutation");
            isDenied(() -> stmt.executeQuery(
                "SELECT * FROM b62g_proof.b62g13_emergency_audit"),
                "read-only verifier attempted private audit access");
        }
        try (Connection writer = login(WRITER, writerPassword);
             var stmt = writer.createStatement()) {
            isDenied(() -> stmt.executeUpdate(
                "DELETE FROM b62g_proof.principal_epochs WHERE principal_id=42"),
                "function-only emergency writer attempted table DELETE");
        }

        check(notEmergencyBlocked(runtime, token42a, URL, readerPassword),
            "Unrevoked real signed JWT unexpectedly blocked");
        check(notEmergencyBlocked(runtime, token42b, URL, readerPassword),
            "Second real JWT unexpectedly blocked");
        check(notEmergencyBlocked(runtime, token77, URL, readerPassword),
            "Independent signed UID unexpectedly blocked");

        // Database login and network failures MUST deny, despite valid JWTs.
        check(!notEmergencyBlocked(runtime, token42a, URL,
                readerPassword + "-bad"), "Wrong-password database read allowed");
        check(!notEmergencyBlocked(runtime, token42a, BAD_NETWORK_URL,
                readerPassword), "Disconnected primary allowed stale verdict");
        var wrongSigner = new RuntimePinnedPrincipalVerifier(
            pem(generator.generateKeyPair()), KID, ISS, AUD, Clock.systemUTC());
        check(!notEmergencyBlocked(wrongSigner, token42a, URL, readerPassword),
            "Failed cryptographic verification must not allow B62 lookup");
        check(countAudit(auditorPassword) == 0,
            "Synthetic audit should start empty");

        // A separate authenticated G13 function-only writer commits an
        // inclusive B62-G1 UID cutoff, with an atomic, reviewer-visible audit.
        // Genuine Identity issue() calls can cross a second boundary. B62-G1
        // rejects token iat <= cutoff, not every JWT with the same UID.
        // Choose a committed cutoff covering BOTH independently issued JTIs.
        long cutoff = Math.max(
            signed42.signedIssuedAt().getEpochSecond(),
            signed42b.signedIssuedAt().getEpochSecond());
        try (Connection writer = login(WRITER, writerPassword)) {
            writer.setAutoCommit(false);
            try (PreparedStatement up = writer.prepareStatement(
                "SELECT b62g_proof.b62g13_raise_epoch(?,?,?)")) {
                up.setLong(1, 42L);
                up.setLong(2, cutoff);
                up.setString(3, "ci-" + UUID.randomUUID());
                try (var result = up.executeQuery()) {
                    check(result.next() && result.getLong(1) == cutoff,
                        "G13 monotonic emergency cutoff not stored");
                }
            }
            check(notEmergencyBlocked(runtime, token42a, URL, readerPassword),
                "Uncommitted remote revocation must not be treated as committed");
            writer.commit();
        }
        check(!notEmergencyBlocked(runtime, token42a, URL, readerPassword),
            "Next separate primary read after COMMIT must deny same-IAT JWT");
        check(!notEmergencyBlocked(runtime, token42b, URL, readerPassword),
            "Signed JTI is NOT an override for committed UID epoch");
        check(notEmergencyBlocked(runtime, token77, URL, readerPassword),
            "Unrelated UID must remain unaffected by principal-42 cutoff");
        check(countAudit(auditorPassword) == 1,
            "Emergency epoch must be accompanied by atomic reviewer audit");

        // A blocked issuer/kid is global across both synthetic users, not a
        // jti-specific flag; verify the real G13 function-only write path.
        try (Connection writer = login(WRITER, writerPassword);
             PreparedStatement block = writer.prepareStatement(
                 "SELECT b62g_proof.b62g13_block_signer(?,?,?)")) {
            block.setString(1, ISS);
            block.setString(2, KID);
            block.setString(3, "ci-" + UUID.randomUUID());
            block.executeQuery().close();
        }
        check(!notEmergencyBlocked(runtime, token77, URL, readerPassword),
            "Committed signer block must deny independent user's signed token");
        check(countAudit(auditorPassword) == 2,
            "Signer block must have independent immutable-request audit entry");

        // A valid B62-G1 post-signature result is NOT proof of a live session;
        // no production filter or task/mission/SSE route is enabled here.
        System.out.println("IDENTITY_RUNTIME_B62_AUTHENTICATED_PRIMARY_JDBC_REVOKE_PASS");
        System.out.println("IDENTITY_RUNTIME_B62_JTI_IS_NOT_SESSION_GRANT_NO_GO");
    }
}
