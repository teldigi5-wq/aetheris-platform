package io.aetheris.users.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEW-ONLY replay-admission negative witness. Uses actual PostgreSQL16 and
 * RabbitMQ4 in the uniquely named ephemeral CI project. This class is a test,
 * NOT a production consumer, authenticated broker freeze or independent epoch
 * witness; deliberately does not modify application listeners or migrations.
 */
class BrokerSignedReplayAdmissionIT {
    private static final String ISSUER = "aetheris-replay-review";
    private static final String AUDIENCE = "aetheris-audit-recovery";
    private static final String KID = "xerj-ci-rs256-replay-only";
    private static final String PRINCIPAL = "synthetic-replay-worker";
    private static final String SCOPE = "audit:replay";
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String OTHER_TENANT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String DB = "xerj_fence_audit";
    private static final String FENCE_B = "88888888-8888-4888-8888-888888888888";

    private enum Admission { APPLIED, DUPLICATE, DENIED }
    private record Verified(String principal, UUID tenant, long generation, UUID eventId) {}

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing isolated CI fixture: " + name);
        }
        return value;
    }

    private static void isolated() {
        String run = required("GITHUB_RUN_ID");
        String attempt = required("GITHUB_RUN_ATTEMPT");
        String project = required("XERJ_COMPOSE_PROJECT");
        if (!"true".equals(required("GITHUB_ACTIONS"))
                || !run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !project.equals("xerj-mtls-ci-" + run + "-" + attempt)
                || !required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Not a unique ephemeral GitHub runner");
        }
    }

    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        return generator.generateKeyPair();
    }

    private static String token(KeyPair signingKey, UUID eventId, long generation,
                                String principal, String tenant, String scope,
                                String issuer, String audience, String kid,
                                Instant issued, Instant expiry) {
        return Jwts.builder()
                .subject(principal).issuer(issuer).audience().add(audience).and()
                .id(eventId.toString())
                .claim("tid", tenant).claim("generation", generation)
                .claim("scopes", List.of(scope))
                .issuedAt(Date.from(issued)).expiration(Date.from(expiry))
                .header().keyId(kid).and()
                .signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static String token(KeyPair signingKey, UUID eventId, long generation) {
        Instant now = Instant.now();
        return token(signingKey, eventId, generation, PRINCIPAL, TENANT,
                SCOPE, ISSUER, AUDIENCE, KID,
                now.minusSeconds(5), now.plusSeconds(180));
    }

    private static Verified verify(String compact, PublicKey verifier) {
        if (compact == null || compact.isBlank() || compact.length() > 8192) {
            throw new IllegalArgumentException("Missing or oversized signed replay capability");
        }
        var signed = Jwts.parser().verifyWith(verifier)
                .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .build().parseSignedClaims(compact);
        if (!"RS256".equals(signed.getHeader().getAlgorithm())
                || !KID.equals(signed.getHeader().getKeyId())) {
            throw new IllegalArgumentException("Unpinned signing algorithm/key");
        }
        Claims claims = signed.getPayload();
        Instant now = Instant.now();
        if (claims.getIssuedAt() == null || claims.getExpiration() == null
                || claims.getIssuedAt().toInstant().isAfter(now.plusSeconds(30))
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getExpiration().toInstant()
                        .isAfter(claims.getIssuedAt().toInstant().plusSeconds(300))
                || !(claims.get("scopes") instanceof Collection<?> scopes)
                || scopes.size() != 1 || !scopes.contains(SCOPE)
                || !(claims.get("generation") instanceof Number rawGeneration)
                || rawGeneration.longValue() <= 0
                || rawGeneration.doubleValue() != rawGeneration.longValue()) {
            throw new IllegalArgumentException("Untrusted replay scope/lifetime/generation");
        }
        String tid = claims.get("tid", String.class);
        if (tid == null || claims.getSubject() == null || claims.getSubject().isBlank()
                || claims.getId() == null) {
            throw new IllegalArgumentException("Missing signed replay fields");
        }
        UUID tenant = UUID.fromString(tid);
        UUID eventId = UUID.fromString(claims.getId());
        if (!tid.equals(tenant.toString()) || !claims.getId().equals(eventId.toString())) {
            throw new IllegalArgumentException("Noncanonical signed IDs");
        }
        return new Verified(claims.getSubject(), tenant,
                rawGeneration.longValue(), eventId);
    }

    private static PGSimpleDataSource source() {
        PGSimpleDataSource data = new PGSimpleDataSource();
        data.setURL("jdbc:postgresql://127.0.0.1:55432/" + DB);
        data.setUser("xerj_ci");
        data.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return data;
    }

    /** Scoped test-side policy, executed atomically WITH synthetic side effect
     * inside one PostgreSQL transaction. It does not guard the real listener. */
    private static final class TestAdmissionGate {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transaction;
        private final PublicKey publicKey;

        TestAdmissionGate(PGSimpleDataSource data, PublicKey publicKey) {
            this.jdbc = new JdbcTemplate(data);
            this.transaction = new TransactionTemplate(new DataSourceTransactionManager(data));
            this.publicKey = publicKey;
        }

        Admission admit(String compact) {
            final Verified signed;
            try {
                signed = verify(compact, publicKey);
            } catch (Exception rejected) {
                return Admission.DENIED;
            }
            try {
                Admission outcome = transaction.execute(status -> {
                    // Lock the generation fence in the SAME transaction as the
                    // synthetic effect: concurrent phase changes serialize.
                    Map<String, Object> authority = jdbc.queryForMap("""
                        SELECT generation, phase, tenant_id::text AS tenant
                        FROM public.xerj_review_replay_authority
                        WHERE singleton=1 FOR UPDATE
                        """);
                    Number current = (Number) authority.get("generation");
                    if (current.longValue() != signed.generation()
                            || !"ACTIVE".equals(authority.get("phase"))
                            || !signed.tenant().toString().equals(authority.get("tenant"))) {
                        return Admission.DENIED;
                    }
                    var principals = jdbc.query("""
                        SELECT tenant_id::text AS tenant, scope, revoked
                        FROM public.xerj_review_replay_principal
                        WHERE principal_id=? FOR SHARE
                        """, (rs, row) -> new Object[]{
                            rs.getString("tenant"), rs.getString("scope"), rs.getBoolean("revoked")
                        }, signed.principal());
                    if (principals.size() != 1) return Admission.DENIED;
                    Object[] principal = principals.get(0);
                    if (!signed.tenant().toString().equals(principal[0])
                            || !SCOPE.equals(principal[1]) || Boolean.TRUE.equals(principal[2])) {
                        return Admission.DENIED;
                    }
                    int inserted = jdbc.update("""
                        INSERT INTO public.xerj_review_replay_effects
                            (event_id,tenant_id,principal_id,generation)
                        VALUES (? ,?::uuid,?,?) ON CONFLICT (event_id) DO NOTHING
                        """, signed.eventId(), signed.tenant().toString(),
                            signed.principal(), signed.generation());
                    return inserted == 1 ? Admission.APPLIED : Admission.DUPLICATE;
                });
                return outcome == null ? Admission.DENIED : outcome;
            } catch (DataAccessException rejected) {
                return Admission.DENIED;
            }
        }
    }

    private static void setup(JdbcTemplate jdbc) {
        assertEquals(FENCE_B, jdbc.queryForObject(
                "SELECT token::text FROM public.xerj_dr_fence", String.class));
        assertEquals(TENANT, jdbc.queryForObject("""
                SELECT tenant_id::text FROM public.audit_events
                WHERE event_id='66666666-6666-4666-8666-666666666666'
                """, String.class));
        jdbc.execute("""
                CREATE TABLE public.xerj_review_replay_authority (
                    singleton INTEGER PRIMARY KEY CHECK (singleton=1),
                    generation BIGINT NOT NULL CHECK (generation > 0),
                    phase TEXT NOT NULL CHECK (phase IN ('ACTIVE','FROZEN')),
                    tenant_id UUID NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE public.xerj_review_replay_principal (
                    principal_id TEXT PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    scope TEXT NOT NULL,
                    revoked BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE public.xerj_review_replay_effects (
                    event_id UUID PRIMARY KEY,
                    tenant_id UUID NOT NULL,
                    principal_id TEXT NOT NULL,
                    generation BIGINT NOT NULL
                )
                """);
        jdbc.update("""
                INSERT INTO public.xerj_review_replay_authority
                    (singleton, generation, phase, tenant_id)
                VALUES (1,1,'ACTIVE',?::uuid)
                """, TENANT);
        jdbc.update("""
                INSERT INTO public.xerj_review_replay_principal
                    (principal_id,tenant_id,scope,revoked)
                VALUES (?,?::uuid,?,false)
                """, PRINCIPAL, TENANT, SCOPE);
    }

    private static void clean(JdbcTemplate jdbc) {
        jdbc.execute("DROP TABLE IF EXISTS public.xerj_review_replay_effects");
        jdbc.execute("DROP TABLE IF EXISTS public.xerj_review_replay_principal");
        jdbc.execute("DROP TABLE IF EXISTS public.xerj_review_replay_authority");
    }

    private static void publish(Channel channel, String queue, String compact) throws Exception {
        channel.confirmSelect();
        channel.basicPublish("", queue,
                new AMQP.BasicProperties.Builder()
                        .deliveryMode(2).contentType("text/plain").build(),
                compact.getBytes(StandardCharsets.UTF_8));
        channel.waitForConfirmsOrDie(5000);
    }

    @Test
    void signedGenerationScopedReplayWithActualBrokerDeadLetterAndDbCommit()
            throws Exception {
        isolated();
        PGSimpleDataSource data = source();
        JdbcTemplate jdbc = new JdbcTemplate(data);
        setup(jdbc);
        KeyPair owner = keyPair();
        KeyPair attacker = keyPair();
        TestAdmissionGate gate = new TestAdmissionGate(data, owner.getPublic());
        Instant now = Instant.now();
        try {
            UUID firstEvent = UUID.randomUUID();
            String oldToken = token(owner, firstEvent, 1);
            assertEquals(Admission.DENIED, gate.admit(""),
                    "No bearer or forged identity header must never authorize replay");
            assertEquals(Admission.APPLIED, gate.admit(oldToken));
            assertEquals(Admission.DUPLICATE, gate.admit(oldToken));

            // Freezing the local synthetic gate revokes prior generation
            // capabilities EVEN WHEN their RS256 signature is still valid.
            assertEquals(1, jdbc.update("""
                    UPDATE public.xerj_review_replay_authority
                    SET generation=2, phase='FROZEN' WHERE singleton=1
                    """));
            assertEquals(Admission.DENIED, gate.admit(oldToken));
            String frozenSecond = token(owner, UUID.randomUUID(), 2);
            assertEquals(Admission.DENIED, gate.admit(frozenSecond));
            jdbc.update("""
                    UPDATE public.xerj_review_replay_authority
                    SET phase='ACTIVE' WHERE singleton=1
                    """);
            assertEquals(Admission.DENIED, gate.admit(oldToken));

            UUID freshEvent = UUID.randomUUID();
            String newToken = token(owner, freshEvent, 2);
            assertEquals(Admission.APPLIED, gate.admit(newToken));
            assertEquals(Admission.DUPLICATE, gate.admit(newToken));

            jdbc.update("""
                    UPDATE public.xerj_review_replay_principal
                    SET revoked=true WHERE principal_id=?
                    """, PRINCIPAL);
            assertEquals(Admission.DENIED,
                    gate.admit(token(owner, UUID.randomUUID(), 2)),
                    "Revocation checked on every replay, not cached at issuance");
            jdbc.update("""
                    UPDATE public.xerj_review_replay_principal
                    SET revoked=false WHERE principal_id=?
                    """, PRINCIPAL);

            String wrongTenant = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    OTHER_TENANT, SCOPE, ISSUER, AUDIENCE, KID,
                    now.minusSeconds(5), now.plusSeconds(180));
            String wrongScope = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    TENANT, "audit:read", ISSUER, AUDIENCE, KID,
                    now.minusSeconds(5), now.plusSeconds(180));
            String wrongIssuer = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    TENANT, SCOPE, "forged-issuer", AUDIENCE, KID,
                    now.minusSeconds(5), now.plusSeconds(180));
            String wrongAudience = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    TENANT, SCOPE, ISSUER, "untrusted-audience", KID,
                    now.minusSeconds(5), now.plusSeconds(180));
            String wrongKid = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    TENANT, SCOPE, ISSUER, AUDIENCE, "fake-kid",
                    now.minusSeconds(5), now.plusSeconds(180));
            String expired = token(owner, UUID.randomUUID(), 2, PRINCIPAL,
                    TENANT, SCOPE, ISSUER, AUDIENCE, KID,
                    now.minusSeconds(200), now.minusSeconds(50));
            String forgedSignature = token(attacker, UUID.randomUUID(), 2);
            for (String invalid : List.of(wrongTenant, wrongScope, wrongIssuer,
                    wrongAudience, wrongKid, expired, forgedSignature)) {
                assertEquals(Admission.DENIED, gate.admit(invalid));
            }

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("127.0.0.1");
            factory.setPort(55672);
            factory.setVirtualHost("xerj_review");
            factory.setUsername("xerj_ci");
            factory.setPassword(required("XERJ_TEST_RABBIT_PASSWORD"));
            factory.setConnectionTimeout(5000);
            String suffix = UUID.randomUUID().toString();
            String exchange = "xerj.replay.dlx." + suffix;
            String queue = "xerj.replay.source." + suffix;
            String quarantine = "xerj.replay.quarantine." + suffix;
            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {
                channel.exchangeDeclare(exchange, "direct", true);
                channel.queueDeclare(quarantine, true, false, false,
                        Map.of("x-expires", 300_000L));
                channel.queueBind(quarantine, exchange, "review-denied");
                channel.queueDeclare(queue, true, false, false, Map.of(
                        "x-expires", 300_000L,
                        "x-dead-letter-exchange", exchange,
                        "x-dead-letter-routing-key", "review-denied"));
                try {
                    // Real persistent stale RabbitMQ redelivery cannot trigger
                    // a new effect after the synthetic generation advanced.
                    publish(channel, queue, oldToken);
                    GetResponse stale = channel.basicGet(queue, false);
                    assertNotNull(stale);
                    assertEquals(Admission.DENIED, gate.admit(
                            new String(stale.getBody(), StandardCharsets.UTF_8)));
                    channel.basicReject(stale.getEnvelope().getDeliveryTag(), false);
                    GetResponse quarantined = null;
                    for (int i = 0; i < 30; i++) {
                        quarantined = channel.basicGet(quarantine, false);
                        if (quarantined != null) break;
                        Thread.sleep(100);
                    }
                    assertNotNull(quarantined,
                            "Denied stale delivery must go to test-only quarantine");
                    assertArrayEquals(stale.getBody(), quarantined.getBody());
                    channel.basicAck(quarantined.getEnvelope().getDeliveryTag(), false);

                    UUID admitted = UUID.randomUUID();
                    String fresh = token(owner, admitted, 2);
                    publish(channel, queue, fresh);
                    GetResponse permitted = channel.basicGet(queue, false);
                    assertNotNull(permitted);
                    assertEquals(Admission.APPLIED, gate.admit(
                            new String(permitted.getBody(), StandardCharsets.UTF_8)));
                    channel.basicAck(permitted.getEnvelope().getDeliveryTag(), false);
                    assertEquals(Admission.DUPLICATE, gate.admit(fresh));

                    assertEquals(3, jdbc.queryForObject(
                            "SELECT count(*) FROM public.xerj_review_replay_effects",
                            Integer.class));
                    assertEquals(0, jdbc.queryForObject(
                            "SELECT count(*) FROM public.xerj_review_replay_effects " +
                            "WHERE generation=2 AND event_id=?",
                            Integer.class, firstEvent));
                    System.out.println(
                            "XERJ_SIGNED_REPLAY_EPOCH_SCOPE_REVOCATION_AND_QUARANTINE_PASS");
                } finally {
                    channel.queueDelete(queue);
                    channel.queueDelete(quarantine);
                    channel.exchangeDelete(exchange);
                }
            }
        } finally {
            clean(jdbc);
        }
    }
}
