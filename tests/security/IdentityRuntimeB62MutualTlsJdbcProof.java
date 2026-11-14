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
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    /**
     * Explicitly bounded, fixed-command CI fixture controller. Never accepts
     * user-supplied shell commands, network hosts, passwords, or key material.
     * Script independently verifies exact GitHub disposable runner constraints.
     */
    private static void syntheticFixtureAction(String action, int pid)
            throws Exception {
        must("true".equals(env("GITHUB_ACTIONS"))
                && "yes".equals(env("B62_MTLS_CI_ONLY")),
            "Certificate trust mutations are permitted only inside synthetic CI");
        must(List.of("overlap", "cutover", "terminate").contains(action),
            "Unexpected privileged synthetic fixture command");
        var command = new java.util.ArrayList<>(List.of(
            "bash", "tests/security/b62g14-mtls-disposable-db.sh", action));
        if ("terminate".equals(action)) {
            must(pid > 0 && pid < 1000000000,
                "Refuse malformed disposable reader backend PID");
            command.add(Integer.toString(pid));
        } else {
            must(pid == 0, "Unexpected PID on trust-store rotation");
        }
        var process = new ProcessBuilder(command).inheritIO().start();
        if (!process.waitFor(25, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Timed out changing disposable B62 client CA trust");
        }
        must(process.exitValue() == 0,
            "Disposable B62 trust-rotation action did not complete");
    }

    private static int backendPid(Connection connection) throws SQLException {
        try (var q = connection.prepareStatement("SELECT pg_backend_pid()");
             var row = q.executeQuery()) {
            if (!row.next()) throw new SQLException("Synthetic reader backend missing");
            return row.getInt(1);
        }
    }

    private static void assertOldConnectionClosed(Connection prior) throws Exception {
        boolean terminated = false;
        for (int i = 0; i < 15; i++) {
            try (var check = prior.prepareStatement("SELECT current_user");
                 var rows = check.executeQuery()) {
                if (rows.next()) {
                    Thread.sleep(100);
                }
            } catch (SQLException killed) {
                terminated = true;
                break;
            }
        }
        must(terminated,
            "A removed client trust root does not kill existing sessions: "
                + "the explicit synthetic backend retirement must terminate it");
    }

    /**
     * A test connection observing a RUNNING backend SQL statement, rather
     * than merely a thread-start signal, closes the timing loophole in an
     * in-flight pool-drain regression. Both sessions have the same synthetic
     * reader database role; the new certificate is independently verified.
     */
    private static void awaitInFlightReaderSql(
            String newClient, String password, int expectedPid) throws Exception {
        try (Connection observer = connect(newClient, READER, password);
             PreparedStatement query = observer.prepareStatement("""
                SELECT EXISTS (
                  SELECT 1 FROM pg_stat_activity
                  WHERE pid = ? AND datname = 'b62g_synthetic'
                    AND usename = 'b62g8_user_reader'
                    AND state = 'active'
                    AND query LIKE 'SELECT pg_sleep(12)%'
                )
                """)) {
            query.setInt(1, expectedPid);
            for (int i = 0; i < 40; i++) {
                try (var row = query.executeQuery()) {
                    if (row.next() && row.getBoolean(1)) return;
                }
                Thread.sleep(100);
            }
        }
        throw new AssertionError(
            "Cannot prove old-certificate SQL reached PostgreSQL ACTIVE state");
    }

    private static void verifyDisposableReaderClientCaRotation(String password)
            throws Exception {
        String newClient = url("localhost", READER + ".next", "ca.crt",
            true, "verify-full");
        rejects(newClient, READER, password,
            "next reader CA not yet added to server's client trust");

        // TWO independent, previously authenticated old-certificate
        // connections model an idle pool member and a busy pool member.
        // The longer socket timeout on the busy member ensures its sleep is
        // interrupted by explicit backend retirement, not client timeout.
        String oldBusyUrl = correctUrl(READER).replace(
            "&socketTimeout=3", "&socketTimeout=20");
        must(oldBusyUrl.contains("&socketTimeout=20"),
            "In-flight query must not be confused with socket timeout");
        try (Connection oldIdle = connect(correctUrl(READER), READER, password);
             Connection oldBusy = connect(oldBusyUrl, READER, password)) {
            int idlePid = backendPid(oldIdle);
            int busyPid = backendPid(oldBusy);
            must(idlePid != busyPid, "Pool members must use independent PostgreSQL PIDs");

            syntheticFixtureAction("overlap", 0);
            try (Connection oldFresh = connect(correctUrl(READER), READER, password);
                 Connection newFresh = connect(newClient, READER, password)) {
                must(backendPid(oldFresh) != backendPid(newFresh),
                    "Old and new workload identities must have distinct TLS sessions");
            }
            System.out.println("B62_OLD_AND_NEW_READER_CERTS_BOTH_TRUSTED_DURING_OVERLAP");

            syntheticFixtureAction("cutover", 0);
            try (Connection newAfter = connect(newClient, READER, password)) {
                must(READER.equals(newAfter.getMetaData().getUserName()),
                    "Replacement mTLS reader identity incorrect");
            }
            rejects(correctUrl(READER), READER, password,
                "old signed reader certificate accepted after CA removal");
            System.out.println("B62_REMOVED_CLIENT_CA_REJECTS_NEW_OLD_CERT_HANDSHAKES");

            // Both old TLS connections still work until actively retired.
            for (Connection old : new Connection[]{oldIdle, oldBusy}) {
                try (var sql = old.prepareStatement("SELECT current_user");
                     var row = sql.executeQuery()) {
                    must(row.next() && READER.equals(row.getString(1)),
                        "Prior authenticated old certificate was retroactively denied");
                }
            }
            System.out.println("B62_OLD_CERT_SESSION_STAYS_ACTIVE_UNTIL_EXPLICIT_RETIREMENT");
            System.out.println("B62_TWO_OLD_POOL_MEMBERS_STILL_AUTHENTICATED_AFTER_CA_REMOVAL");

            // Issue SQL through the second old connection and observe it
            // actually ACTIVE in pg_stat_activity before targeted termination.
            var running = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try (PreparedStatement sleep = oldBusy.prepareStatement(
                        "SELECT pg_sleep(12)")) {
                    sleep.execute();
                    return false; // An uninterrupted old query is a test failure.
                } catch (SQLException terminated) {
                    return true;
                }
            });
            awaitInFlightReaderSql(newClient, password, busyPid);
            System.out.println("B62_OLD_CERT_INFLIGHT_QUERY_OBSERVED_ACTIVE_ON_POSTGRES");

            syntheticFixtureAction("terminate", busyPid);
            must(running.get(5, TimeUnit.SECONDS),
                "An active query survived its targeted certificate-session retirement");
            assertOldConnectionClosed(oldBusy);
            System.out.println("B62_OLD_CERT_INFLIGHT_QUERY_TERMINATED_BY_BACKEND_RETIREMENT");

            // Retiring the busy backend must not cancel unrelated old
            // sessions or newly authenticated replacement sessions.
            try (var query = oldIdle.prepareStatement("SELECT current_user");
                 var rows = query.executeQuery()) {
                must(rows.next() && READER.equals(rows.getString(1)),
                    "Retiring one old busy session killed unrelated idle pool member");
            }
            try (Connection newSurvivor = connect(newClient, READER, password)) {
                must(backendPid(newSurvivor) > 0,
                    "Replacement mTLS reader was lost during old-session retirement");
            }
            System.out.println("B62_OTHER_POOL_MEMBER_AND_NEW_READER_SURVIVE_SINGLE_RETIREMENT");

            // The old idle member must ALSO be explicitly retired; a pool
            // manager cannot stop after replacing only its in-flight member.
            syntheticFixtureAction("terminate", idlePid);
            assertOldConnectionClosed(oldIdle);
            rejects(correctUrl(READER), READER, password,
                "Old CA reader was allowed to reconnect after full retirement");
            try (Connection replacement = connect(newClient, READER, password)) {
                must(backendPid(replacement) > 0,
                    "New reader identity must remain usable after full pool retirement");
            }
            System.out.println("B62_ALL_OLD_READER_POOL_MEMBERS_RETIRED_NEW_READER_CONTINUES");
            System.out.println("B62_RETIRED_OLD_SESSION_AND_REPLACEMENT_READER_CONTINUES");
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

    // REVIEW-ONLY: synthetic /__s0 loopback HTTP+SSE, NOT Runtime endpoints.
    // Auth facts come from real JwtService.issue + immutable Runtime verifier.
    // Each response boundary uses a fresh B62-G1/G8 mTLS PostgreSQL query.
    private static void httpSseRevocationRace(String readPass, String writerPass,
                                              String auditPass) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair ephemeral = generator.generateKeyPair();
        String kid = "s0-http-sse-separate-identity";
        var issuer = new JwtService("", 300, ISSUER, AUDIENCE, "rs256",
            Base64.getEncoder().encodeToString(
                ephemeral.getPrivate().getEncoded()), kid);
        var verifier = new RuntimePinnedPrincipalVerifier(
            publicPem(ephemeral), kid, ISSUER, AUDIENCE, Clock.systemUTC());
        String token = issuer.issue(account(99L));
        String unaffected = issuer.issue(account(100L));
        var signed = verifier.verifyBearerForRevocation("Bearer " + token)
            .requireRevocationFacts();
        must(signed.signedIdentityUid() == 99L
            && UUID.fromString(signed.signedTokenId()).version() == 4,
            "Actual Platform-to-Runtime RS256 facts failed");

        var httpStarted = new java.util.concurrent.CountDownLatch(1);
        var httpResume = new java.util.concurrent.CountDownLatch(1);
        var sseStarted = new java.util.concurrent.CountDownLatch(1);
        var sseResume = new java.util.concurrent.CountDownLatch(1);
        var sseClosed = new java.util.concurrent.CountDownLatch(1);
        var sseDenied = new java.util.concurrent.atomic.AtomicInteger();
        var workers = java.util.concurrent.Executors.newFixedThreadPool(6);
        var http = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress(
                java.net.InetAddress.getByName("127.0.0.1"),0),0);
        String firstFrame = "event: ready\ndata: synthetic-only\n\n";

        http.createContext("/__s0/http", exchange -> {
            try {
                String bearer = exchange.getRequestHeaders().getFirst("Authorization");
                verifier.verifyBearerForRevocation(bearer).requireRevocationFacts();
                String raw = bearer.substring(7);
                if (!readNotBlocked(verifier, raw, readPass)) {
                    exchange.sendResponseHeaders(404,-1);
                } else {
                    httpStarted.countDown();
                    if (!httpResume.await(8,java.util.concurrent.TimeUnit.SECONDS)
                        || !readNotBlocked(verifier, raw, readPass))
                        exchange.sendResponseHeaders(404,-1);
                    else exchange.sendResponseHeaders(204,-1);
                }
            } catch (IllegalArgumentException invalid) {
                exchange.sendResponseHeaders(401,-1);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(503,-1);
            } finally { exchange.close(); }
        });
        http.createContext("/__s0/sse", exchange -> {
            try {
                String bearer = exchange.getRequestHeaders().getFirst("Authorization");
                verifier.verifyBearerForRevocation(bearer).requireRevocationFacts();
                String raw = bearer.substring(7);
                if (!readNotBlocked(verifier, raw, readPass)) {
                    exchange.sendResponseHeaders(404,-1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                exchange.sendResponseHeaders(200,0);
                try (var out = exchange.getResponseBody()) {
                    out.write(firstFrame.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                    out.flush();
                    sseStarted.countDown();
                    if (!sseResume.await(8,java.util.concurrent.TimeUnit.SECONDS))
                        return;
                    // A 200 SSE response is already committed: revoke by
                    // closing BEFORE another event, never by rewriting status.
                    if (!readNotBlocked(verifier, raw, readPass)) {
                        sseDenied.incrementAndGet();
                        return;
                    }
                    out.write("event: later\ndata: synthetic-only\n\n".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                    out.flush();
                } finally { sseClosed.countDown(); }
            } catch (IllegalArgumentException invalid) {
                exchange.sendResponseHeaders(401,-1);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        http.setExecutor(workers);
        http.start();
        try {
            var root = java.net.URI.create("http://127.0.0.1:"
                + http.getAddress().getPort());
            var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build();
            java.util.function.BiFunction<String,String,java.net.http.HttpRequest>
                request = (route,jwt) -> {
                    var b = java.net.http.HttpRequest.newBuilder(root.resolve(route))
                        .timeout(java.time.Duration.ofSeconds(18)).GET();
                    if (jwt != null)
                        b.header("Authorization","Bearer " + jwt);
                    return b.build();
                };

            var runningHttp = client.sendAsync(
                request.apply("/__s0/http",token),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            must(httpStarted.await(6,java.util.concurrent.TimeUnit.SECONDS),
                "Real HTTP did not complete initial B62 primary check");
            var subscription = client.sendAsync(
                request.apply("/__s0/sse",token),
                java.net.http.HttpResponse.BodyHandlers.ofInputStream())
                .get(6,java.util.concurrent.TimeUnit.SECONDS);
            must(subscription.statusCode()==200
                && sseStarted.await(6,java.util.concurrent.TimeUnit.SECONDS),
                "Real SSE not initially connected and flushed");
            try (var body = subscription.body()) {
                byte[] ready = body.readNBytes(firstFrame.getBytes(
                    java.nio.charset.StandardCharsets.UTF_8).length);
                must(firstFrame.equals(new String(ready,
                    java.nio.charset.StandardCharsets.UTF_8)),
                    "Initial synthetic SSE frame not received via real TCP");
                must(!runningHttp.isDone(),"HTTP completed before B62 revocation");

                // An independent client-cert-authenticated G13 writer commits
                // after both HTTP and SSE have passed their initial checks.
                try (Connection writer=connect(correctUrl(WRITER),WRITER,writerPass)) {
                    writer.setAutoCommit(false);
                    try (PreparedStatement p=writer.prepareStatement(
                        "SELECT b62g_proof.b62g13_raise_epoch(?,?,?)")) {
                        p.setLong(1,99L);
                        p.setLong(2,signed.signedIssuedAt().getEpochSecond());
                        p.setString(3,"ci-" + UUID.randomUUID());
                        try (var rows=p.executeQuery()) {
                            must(rows.next(),"B62 G13 function-only revoke failed");
                        }
                    }
                    writer.commit();
                }
                must(auditableEvents(auditPass)==3,
                    "Third B62 G13 action has no independent audit");
                must(!readNotBlocked(verifier,token,readPass),
                    "Post-commit B62 primary returned stale allow");
                httpResume.countDown();
                sseResume.countDown();
                var after=runningHttp.get(7,java.util.concurrent.TimeUnit.SECONDS);
                must(after.statusCode()==404 && after.body().isEmpty(),
                    "HTTP sent success or payload after committed revocation");
                must(sseClosed.await(7,java.util.concurrent.TimeUnit.SECONDS),
                    "Existing SSE connection not closed after committed revocation");
                byte[] later;
                try { later=body.readAllBytes(); }
                catch (java.io.IOException closed) { later=new byte[0]; }
                must(later.length==0 && sseDenied.get()==1,
                    "SSE published another event after committed revocation");
            }
            System.out.println("B62_REAL_HTTP_POST_COMMIT_REVALIDATION_DENIES_NO_PAYLOAD");
            System.out.println("B62_REAL_SSE_POST_COMMIT_REVALIDATION_CLOSES_NO_EVENT");
            must(client.send(request.apply("/__s0/http",token),
                java.net.http.HttpResponse.BodyHandlers.ofString())
                .statusCode()==404,"New HTTP admitted already-revoked token");
            must(client.send(request.apply("/__s0/sse",token),
                java.net.http.HttpResponse.BodyHandlers.ofString())
                .statusCode()==404,"New SSE admitted already-revoked token");
            must(readNotBlocked(verifier,unaffected,readPass),
                "Unrelated signed UID incorrectly blocked");
            must(!readNotBlocked(verifier,unaffected,readPass+"-bad"),
                "Bad DB credential returned cached allow");
            System.out.println("B62_REAL_HTTP_SSE_NEW_SUBSCRIPTIONS_DENY_AND_STORE_FAILURE_DENY");
            System.out.println("B62_SYNTHETIC_HTTP_SSE_ONLY_RUNTIME_PRODUCTION_QUARANTINE_NO_GO");
        } finally {
            httpResume.countDown();
            sseResume.countDown();
            http.stop(0);
            workers.shutdownNow();
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

        // Preserve successful signed revocation and audit evidence BEFORE
        // rotating the certificate trust roots, then explicitly test that
        // trust-anchor removal does not retroactively cancel established TLS.
        httpSseRevocationRace(readPass, writerPass, auditorPass);
        verifyDisposableReaderClientCaRotation(readPass);

        System.out.println("B62_ACTUAL_PG16_CLIENT_CERT_AND_SERVER_HOSTNAME_MTLS_PASS");
        System.out.println("B62_REAL_IDENTITY_RUNTIME_TLS_PRIMARY_SIGNED_REVOKE_PASS");
        System.out.println("B62_TLS_PROOF_NOT_PRODUCTION_SESSION_OR_HTTP_SSE_GRANT_NO_GO");
    }
}
