package io.aetheris.identity;

import io.aetheris.orchestrator.security.RuntimePinnedPrincipalVerifier;

import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

/**
 * CI-ONLY: Platform's real Identity issuer -> immutable Runtime RS256
 * verifier -> disposable PostgreSQL16 B62 G1/G2/G8/G13 over mutual TLS.
 *
 * PostgreSQL itself checks client certificate CN against the authenticated
 * role AND verifies SCRAM password; pgJDBC validates a server certificate
 * chained to the exact in-memory-generated CI CA with DNS hostname.
 *
 * This is NOT service-to-service HTTP mTLS, a production PKI, issuer rollout,
 * B62 session registration or revocation adapter, ownership permission,
 * live task/SSE admission, replication consistency, or production deployment.
 * The certificates, passwords and server exist only in this disposable CI.
 */
public final class IdentityRuntimeB62MutualTlsJdbcProof {
    private static final String ISSUER = "aetheris-identity";
    private static final String AUDIENCE = "aetheris-orchestrator-api";
    private static final String KID = "b62-ci-tls-signing-key";
    private static final String READER = "b62g8_user_reader";
    private static final String WRITER = "b62g13_emergency_writer";
    private static final String AUDITOR = "b62g13_audit_reviewer";
    private static final int PORT = 55435;

    private IdentityRuntimeB62MutualTlsJdbcProof() {}

    private static void must(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        must(value != null && !value.isBlank(), "Missing CI fixture " + name);
        return value;
    }

    private static String enc(Path path) {
        return URLEncoder.encode(path.toAbsolutePath().toString(),
            StandardCharsets.UTF_8);
    }

    /**
     * The URLs cannot be supplied by the user and cannot reference a replica.
     * Verify-full requires BOTH trusted server CA and exact localhost SAN.
     * PKCS#8 keys are generated in GitHub Actions, never checked into git.
     */
    private static String url(String host, String identity, String ca,
                              boolean withClientCertificate, String mode) {
        Path certs = Path.of(env("B62_MTLS_FIXTURE_DIR"));
        String parameters = "?sslmode=" + mode
            + "&connectTimeout=2&socketTimeout=3"
            + "&sslrootcert=" + enc(certs.resolve(ca));
        if (withClientCertificate) {
            parameters += "&sslcert=" + enc(certs.resolve(identity + ".crt"))
                + "&sslkey=" + enc(certs.resolve(identity + ".pk8"));
        }
        return "jdbc:postgresql://" + host + ":" + PORT
            + "/b62g_synthetic" + parameters;
    }

    private static String correctUrl(String role) {
        return url("localhost", role, "ca.crt", true, "verify-full");
    }

    private static Connection connect(String jdbc, String role, String password)
            throws SQLException {
        var conn = DriverManager.getConnection(jdbc, role, password);
        try (var identity = conn.prepareStatement("SELECT current_user");
             var rows = identity.executeQuery()) {
            if (!rows.next() || !role.equals(rows.getString(1))) {
                conn.close();
                throw new SQLException("Authenticated DB principal mismatch");
            }
        }
        // Confirm the server really negotiated TLS, not merely a JDBC flag.
        try (var stmt = conn.prepareStatement("""
                 SELECT ssl, version, cipher
                 FROM pg_stat_ssl WHERE pid = pg_backend_pid()
                 """);
             var rows = stmt.executeQuery()) {
            if (!rows.next() || !rows.getBoolean(1)
                    || !("TLSv1.3".equals(rows.getString(2))
                        || "TLSv1.2".equals(rows.getString(2)))
                    || rows.getString(3) == null || rows.getString(3).isBlank()) {
                conn.close();
                throw new SQLException("PostgreSQL client transport is not TLS1.2+");
            }
        }
        return conn;
    }

    private static void rejects(String jdbc, String role, String password,
                                String what) throws Exception {
        try (Connection ignored = connect(jdbc, role, password)) {
            throw new AssertionError("Fail-open mTLS identity or trust: " + what);
        } catch (SQLException expected) {
            // Do not display errors containing certificate paths or credentials.
            // Fail-closed is sufficient for all TLS handshake failure variants.
        }
    }

    private static IdentityAccount account(long uid) throws Exception {
        var a = new IdentityAccount("Synthetic",
            "mutual-tls-" + uid + "@example.invalid",
            "unused-ci-only-hash", Role.DEVELOPER);
        Field id = IdentityAccount.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(a, uid);
        must(a.getId() == uid, "Synthetic account ID invalid");
        return a;
    }

    private static String publicPem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    }

    private static RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts
        signedFacts(RuntimePinnedPrincipalVerifier runtime, String token) {
        return runtime.verifyBearerForRevocation("Bearer " + token)
            .requireRevocationFacts();
    }

    private static boolean readNotBlocked(
            RuntimePinnedPrincipalVerifier runtime, String bearer,
            String readerPassword) {
        try {
            // The real Runtime RSA verification must precede any authority read.
            var signed = signedFacts(runtime, bearer);
            try (Connection c = connect(correctUrl(READER), READER,
                    readerPassword)) {
                c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                c.setReadOnly(true);
                c.setAutoCommit(false);
                try (var bounds = c.createStatement()) {
                    bounds.execute("SET LOCAL statement_timeout = '900ms'");
                    bounds.execute("SET LOCAL lock_timeout = '600ms'");
                }
                try (PreparedStatement q = c.prepareStatement("""
                    SELECT current_user = 'b62g8_user_reader'
                      AND current_setting('transaction_read_only') = 'on'
                      AND current_setting('transaction_isolation') = 'read committed'
                      AND current_setting('statement_timeout') = '900ms'
                      AND current_setting('lock_timeout') = '600ms'
                      AND b62g_proof.synthetic_post_signature_allowed(?,?,?,?)
                    """)) {
                    q.setQueryTimeout(2);
                    q.setLong(1, signed.signedIdentityUid());
                    q.setString(2, signed.verifiedIssuer());
                    q.setString(3, signed.verifiedKeyId());
                    q.setLong(4, signed.signedIssuedAt().getEpochSecond());
                    try (var row = q.executeQuery()) {
                        boolean notEmergencyBlocked = row.next() && row.getBoolean(1);
                        c.rollback();
                        return notEmergencyBlocked;
                    }
                }
            }
        } catch (SQLException | RuntimeException rejected) {
            // Connection failure, wrong cert, bad password, store outage,
            // expired or forged JWT: always return DENY, never cached ALLOW.
            return false;
        }
    }

    private static int auditableEvents(String auditorPassword) throws Exception {
        try (Connection c = connect(correctUrl(AUDITOR), AUDITOR, auditorPassword);
             PreparedStatement q = c.prepareStatement(
                 "SELECT count(*) FROM b62g_proof.b62g13_emergency_audit");
             var row = q.executeQuery()) {
            must(row.next(), "B62 audit event query missing");
            return row.getInt(1);
        }
    }

    public static void main(String[] args) throws Exception {
        must(args.length == 0, "No input or endpoint overrides allowed");
        must("true".equals(env("GITHUB_ACTIONS"))
                && "yes".equals(env("B62_MTLS_CI_ONLY"))
                && "b62g_synthetic".equals(env("PGDATABASE"))
                && "127.0.0.1".equals(env("PGHOST")),
            "This is not the isolated GitHub B62 PostgreSQL test");
        Path fixture = Path.of(env("B62_MTLS_FIXTURE_DIR"));
        must(Files.isRegularFile(fixture.resolve("ca.crt")),
            "Synthetic CA certificate missing");
        for (String role : new String[]{READER, WRITER, AUDITOR}) {
            must(Files.isRegularFile(fixture.resolve(role + ".pk8"))
                && Files.isRegularFile(fixture.resolve(role + ".crt")),
                "Synthetic client certificate missing");
        }

        String readPass = env("B62_CI_READER_PASSWORD");
        String writerPass = env("B62_CI_WRITER_PASSWORD");
        String auditorPass = env("B62_CI_AUDITOR_PASSWORD");

        // Require certificate-based client auth AND distinct SCRAM credentials.
        // Each negative changes exactly one trust dimension.
        try (Connection read = connect(correctUrl(READER), READER, readPass);
             Connection write = connect(correctUrl(WRITER), WRITER, writerPass);
             Connection audit = connect(correctUrl(AUDITOR), AUDITOR, auditorPass)) {
            must(read != write && write != audit,
                "PostgreSQL workload identities must use separate connections");
        }
        rejects(url("localhost", READER, "bad-ca.crt", true, "verify-full"),
            READER, readPass, "server signed by a different CA");
        rejects(url("127.0.0.1", READER, "ca.crt", true, "verify-full"),
            READER, readPass, "DNS hostname mismatch against certificate SAN");
        rejects(url("localhost", READER, "ca.crt", false, "verify-full"),
            READER, readPass, "missing client certificate");
        rejects(url("localhost", "unrelated-client", "ca.crt", true, "verify-full"),
            READER, readPass, "trusted but wrong client certificate CN");
        rejects(correctUrl(READER), READER, readPass + "-wrong",
            "correct certificate with bad PostgreSQL password");
        rejects(url("localhost", READER, "ca.crt", true, "disable"),
            READER, readPass, "non-TLS downgrade");
        rejects(correctUrl(READER), WRITER, writerPass,
            "reader certificate replayed for emergency-writer database role");

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair pair = generator.generateKeyPair();
        var issuer = new JwtService("", 300, ISSUER, AUDIENCE, "rs256",
            Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
            KID);
        var verifier = new RuntimePinnedPrincipalVerifier(
            publicPem(pair), KID, ISSUER, AUDIENCE, Clock.systemUTC());
        String token42 = issuer.issue(account(42));
        String token77 = issuer.issue(account(77));
        var facts = signedFacts(verifier, token42);
        must(facts.signedIdentityUid() == 42, "Wrong signed UID");
        must(UUID.fromString(facts.signedTokenId()).version() == 4,
            "Identity must issue signed UUIDv4 JTI");
        must(readNotBlocked(verifier, token42, readPass),
            "Non-blocked real Identity JWT rejected by restricted mTLS reader");
        must(readNotBlocked(verifier, token77, readPass),
            "Other UID incorrectly rejected");
        must(auditableEvents(auditorPass) == 0, "Audit must start empty");

        // The actual restricted function-only B62-G13 writer performs one
        // committed, atomic revocation with a separate mTLS identity.
        long cutoff = facts.signedIssuedAt().getEpochSecond();
        try (Connection writer = connect(correctUrl(WRITER), WRITER, writerPass)) {
            writer.setAutoCommit(false);
            try (PreparedStatement update = writer.prepareStatement(
                 "SELECT b62g_proof.b62g13_raise_epoch(?,?,?)")) {
                update.setLong(1, 42L);
                update.setLong(2, cutoff);
                update.setString(3, "ci-" + UUID.randomUUID());
                try (var row = update.executeQuery()) {
                    must(row.next() && row.getLong(1) == cutoff,
                        "Signed UID cutoff not stored atomically");
                }
            }
            must(readNotBlocked(verifier, token42, readPass),
                "Uncommitted B62 change must not appear committed");
            writer.commit();
        }
        must(!readNotBlocked(verifier, token42, readPass),
            "Committed B62 principal cutoff must deny same-IAT token");
        must(readNotBlocked(verifier, token77, readPass),
            "B62 principal cutoff must not revoke unrelated user");
        must(auditableEvents(auditorPass) == 1, "Revocation must be audit-backed");

        // Full issuer/key emergency block closes all access tokens by that kid,
        // independently of signed token IDs or individual UID cutoffs.
        try (Connection writer = connect(correctUrl(WRITER), WRITER, writerPass);
             PreparedStatement p = writer.prepareStatement(
                 "SELECT b62g_proof.b62g13_block_signer(?,?,?)")) {
            p.setString(1, ISSUER);
            p.setString(2, KID);
            p.setString(3, "ci-" + UUID.randomUUID());
            p.executeQuery().close();
        }
        must(!readNotBlocked(verifier, token77, readPass),
            "Blocked signer must deny other otherwise-valid user");
        must(auditableEvents(auditorPass) == 2,
            "Signer block must create a second independent audit event");

        System.out.println("B62_ACTUAL_PG16_CLIENT_CERT_AND_SERVER_HOSTNAME_MTLS_PASS");
        System.out.println("B62_REAL_IDENTITY_RUNTIME_TLS_PRIMARY_SIGNED_REVOKE_PASS");
        System.out.println("B62_TLS_PROOF_NOT_PRODUCTION_SESSION_OR_HTTP_SSE_GRANT_NO_GO");
    }
}
