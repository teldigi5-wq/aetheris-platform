package io.aetheris.users.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
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
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-only boundary: a separate ephemeral PostgreSQL16 witness survives a
 * simulated Audit-local epoch rollback. Not a non-rollbackable production quorum.
 */
class BrokerSeparateEpochWitnessIT {
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String FENCE = "88888888-8888-4888-8888-888888888888";
    private static final String AUDIENCE = "aetheris-witness-replay-review";
    private static final String ISSUER = "aetheris-review-only-witness-issuer";
    private static final String KID = "xerj-independent-cluster-ci";
    private static final String PRINCIPAL = "synthetic-audit-replay";
    private static final String SCOPE = "audit:replay";

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing CI variable: " + name);
        return value;
    }

    private static void preflight() {
        String run = required("GITHUB_RUN_ID");
        String attempt = required("GITHUB_RUN_ATTEMPT");
        if (!"true".equals(required("GITHUB_ACTIONS"))
                || !run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !required("XERJ_COMPOSE_PROJECT").equals("xerj-mtls-ci-" + run + "-" + attempt)
                || !required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Separate witness requires isolated hosted CI");
        }
    }

    private static PGSimpleDataSource db(String database, int port,
                                         String username, String password) {
        PGSimpleDataSource result = new PGSimpleDataSource();
        result.setURL("jdbc:postgresql://127.0.0.1:" + port + "/" + database
                + "?connectTimeout=2&socketTimeout=4");
        result.setUser(username);
        result.setPassword(password);
        return result;
    }

    private record Capability(UUID event, long generation, String tenant, String principal) {}

    private static String signed(KeyPair key, UUID event, long generation) {
        Instant now = Instant.now();
        return Jwts.builder().subject(PRINCIPAL).id(event.toString())
                .issuer(ISSUER).audience().add(AUDIENCE).and()
                .claim("tid", TENANT).claim("epoch", generation).claim("scope", SCOPE)
                .issuedAt(Date.from(now.minusSeconds(5)))
                .expiration(Date.from(now.plusSeconds(180)))
                .header().keyId(KID).and().signWith(key.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static Capability verified(String token, KeyPair key) {
        var signed = Jwts.parser().verifyWith(key.getPublic())
                .requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .build().parseSignedClaims(token);
        if (!"RS256".equals(signed.getHeader().getAlgorithm())
                || !KID.equals(signed.getHeader().getKeyId())) {
            throw new IllegalArgumentException("Unpinned signing key");
        }
        var claims = signed.getPayload();
        if (claims.getIssuedAt() == null || claims.getExpiration() == null
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getExpiration().toInstant()
                    .isAfter(claims.getIssuedAt().toInstant().plusSeconds(300))
                || !SCOPE.equals(claims.get("scope", String.class))
                || !PRINCIPAL.equals(claims.getSubject())
                || !(claims.get("epoch") instanceof Number epoch)
                || epoch.longValue() <= 0 || epoch.doubleValue() != epoch.longValue()) {
            throw new IllegalArgumentException("Unsupported signed replay capability");
        }
        UUID id = UUID.fromString(claims.getId());
        UUID tid = UUID.fromString(claims.get("tid", String.class));
        if (!id.toString().equals(claims.getId())
                || !tid.toString().equals(claims.get("tid", String.class))) {
            throw new IllegalArgumentException("Noncanonical signed ID");
        }
        return new Capability(id, epoch.longValue(), tid.toString(), claims.getSubject());
    }

    private enum Result { APPLIED, DUPLICATE, DENIED }

    private static final class ReviewGate {
        private final JdbcTemplate audit;
        private final JdbcTemplate witnessReader;
        private final TransactionTemplate tx;
        private final KeyPair key;

        ReviewGate(PGSimpleDataSource auditSource, JdbcTemplate witnessReader, KeyPair key) {
            audit = new JdbcTemplate(auditSource);
            tx = new TransactionTemplate(new DataSourceTransactionManager(auditSource));
            this.witnessReader = witnessReader;
            this.key = key;
        }

        private boolean localOnlyWouldAccept(Capability token) {
            try {
                Map<String, Object> local = audit.queryForMap(
                        "SELECT generation, phase, tenant_id::text AS tid " +
                        "FROM public.xerj_review_audit_epoch WHERE singleton=1");
                return ((Number)local.get("generation")).longValue() == token.generation()
                        && "ACTIVE".equals(local.get("phase"))
                        && token.tenant().equals(local.get("tid"));
            } catch (DataAccessException e) {
                return false;
            }
        }

        Result admit(String jwt) {
            final Capability token;
            final Map<String, Object> remote;
            try {
                token = verified(jwt, key);
                // Independent PostgreSQL cluster with SELECT-only workload
                // credential, not same restored Audit database.
                remote = witnessReader.queryForMap("""
                        SELECT generation, phase, tenant_id::text AS tid
                        FROM public.xerj_epoch_witness WHERE singleton=1
                        """);
            } catch (Exception missingOrInvalidWitness) {
                return Result.DENIED;
            }
            if (((Number)remote.get("generation")).longValue() != token.generation()
                    || !"ACTIVE".equals(remote.get("phase"))
                    || !token.tenant().equals(remote.get("tid"))) {
                return Result.DENIED;
            }
            try {
                Result accepted = tx.execute(status -> {
                    Map<String, Object> local = audit.queryForMap("""
                        SELECT generation, phase, tenant_id::text AS tid
                        FROM public.xerj_review_audit_epoch
                        WHERE singleton=1 FOR UPDATE
                        """);
                    if (((Number)local.get("generation")).longValue() != token.generation()
                            || !"ACTIVE".equals(local.get("phase"))
                            || !token.tenant().equals(local.get("tid"))) {
                        return Result.DENIED;
                    }
                    int updated = audit.update("""
                        INSERT INTO public.xerj_review_witness_effects
                            (event_id,generation,principal_id,tenant_id)
                        VALUES (?,?,?,?::uuid) ON CONFLICT (event_id) DO NOTHING
                        """, token.event(), token.generation(), token.principal(), token.tenant());
                    return updated == 1 ? Result.APPLIED : Result.DUPLICATE;
                });
                return accepted == null ? Result.DENIED : accepted;
            } catch (DataAccessException failure) {
                return Result.DENIED;
            }
        }
    }

    @Test
    void olderAuditSnapshotCannotOverrideSeparatelyAdvancedReadonlyWitness()
            throws Exception {
        preflight();
        PGSimpleDataSource auditSource = db("xerj_fence_audit", 55432,
                "xerj_ci", required("XERJ_TEST_PG_PASSWORD"));
        JdbcTemplate audit = new JdbcTemplate(auditSource);
        assertEquals(FENCE, audit.queryForObject(
                "SELECT token::text FROM public.xerj_dr_fence", String.class));
        PGSimpleDataSource witnessSource = db("xerj_witness_ci", 55433,
                "xerj_witness_reader", required("XERJ_WITNESS_READER_PASSWORD"));
        JdbcTemplate witness = new JdbcTemplate(witnessSource);
        JdbcTemplate coordinator = new JdbcTemplate(db("xerj_witness_ci", 55433,
                "xerj_witness_admin", required("XERJ_WITNESS_ADMIN_PASSWORD")));

        // A workload may observe the second cluster but cannot rewind it.
        assertEquals(1L, witness.queryForObject(
                "SELECT generation FROM public.xerj_epoch_witness", Long.class));
        assertThrows(DataAccessException.class, () -> witness.update(
                "UPDATE public.xerj_epoch_witness SET generation=1 WHERE singleton=1"));
        assertThrows(DataAccessException.class, () -> witness.execute(
                "DROP TABLE public.xerj_epoch_witness"));

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        KeyPair key = generator.generateKeyPair();
        audit.execute("""
                CREATE TABLE public.xerj_review_audit_epoch(
                  singleton INTEGER PRIMARY KEY CHECK (singleton=1),
                  generation BIGINT NOT NULL, phase TEXT NOT NULL, tenant_id UUID NOT NULL)
                """);
        audit.execute("""
                CREATE TABLE public.xerj_review_witness_effects(
                  event_id UUID PRIMARY KEY, generation BIGINT NOT NULL,
                  principal_id TEXT NOT NULL, tenant_id UUID NOT NULL)
                """);
        audit.update("""
                INSERT INTO public.xerj_review_audit_epoch VALUES
                    (1,1,'ACTIVE',?::uuid)
                """, TENANT);
        try {
            ReviewGate gate = new ReviewGate(auditSource, witness, key);
            UUID oldEvent = UUID.randomUUID();
            String oldToken = signed(key, oldEvent, 1);
            assertEquals(Result.APPLIED, gate.admit(oldToken));
            assertEquals(Result.DUPLICATE, gate.admit(oldToken));

            // Only the fixture coordinator (not the workload) advances the
            // separate cluster. Audit can still be restored independently.
            assertEquals(1, coordinator.update("""
                    UPDATE public.xerj_epoch_witness
                    SET generation=2, phase='FROZEN' WHERE singleton=1
                    """));
            Capability old = verified(oldToken, key);
            assertTrue(gate.localOnlyWouldAccept(old),
                    "The restored Audit-local marker remains dangerously permissive");
            assertEquals(Result.DENIED, gate.admit(oldToken),
                    "External advanced witness must override stale Audit-local allow");
            String newToken = signed(key, UUID.randomUUID(), 2);
            assertEquals(Result.DENIED, gate.admit(newToken),
                    "Independent witness FROZEN also denies current generation");

            coordinator.update("""
                    UPDATE public.xerj_epoch_witness SET phase='ACTIVE' WHERE singleton=1
                    """);
            assertEquals(Result.DENIED, gate.admit(oldToken));
            assertEquals(Result.DENIED, gate.admit(newToken),
                    "Audit's old local marker also denies future-generation writes");

            // Operator moves the local fixture forward; the old signed token
            // remains cryptographically valid but can no longer apply.
            audit.update("""
                    UPDATE public.xerj_review_audit_epoch SET generation=2 WHERE singleton=1
                    """);
            assertEquals(Result.APPLIED, gate.admit(newToken));

            // Rewind *only* the local Audit generation, like a stale snapshot.
            audit.update("""
                    UPDATE public.xerj_review_audit_epoch SET generation=1 WHERE singleton=1
                    """);
            assertTrue(gate.localOnlyWouldAccept(old));
            assertEquals(Result.DENIED, gate.admit(oldToken));
            assertEquals(Result.DENIED, gate.admit(newToken));
            assertEquals(2L, witness.queryForObject(
                    "SELECT generation FROM public.xerj_epoch_witness", Long.class));

            // Witness unavailability must never become permission to replay.
            JdbcTemplate absent = new JdbcTemplate(db("xerj_witness_ci", 55434,
                    "xerj_witness_reader", required("XERJ_WITNESS_READER_PASSWORD")));
            ReviewGate outageGate = new ReviewGate(auditSource, absent, key);
            assertEquals(Result.DENIED, outageGate.admit(oldToken));

            // Actual RabbitMQ stale message enters a synthetic DLQ on denial.
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("127.0.0.1");
            factory.setPort(55672);
            factory.setVirtualHost("xerj_review");
            factory.setUsername("xerj_ci");
            factory.setPassword(required("XERJ_TEST_RABBIT_PASSWORD"));
            factory.setConnectionTimeout(5000);
            String suffix = UUID.randomUUID().toString();
            String exchange = "xerj.witness.dlx." + suffix;
            String queue = "xerj.witness.source." + suffix;
            String dlq = "xerj.witness.quarantine." + suffix;
            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {
                channel.exchangeDeclare(exchange, "direct", true);
                channel.queueDeclare(dlq, true, false, false, Map.of("x-expires", 300_000L));
                channel.queueBind(dlq, exchange, "quarantine");
                channel.queueDeclare(queue, true, false, false, Map.of(
                        "x-expires", 300_000L,
                        "x-dead-letter-exchange", exchange,
                        "x-dead-letter-routing-key", "quarantine"));
                try {
                    channel.confirmSelect();
                    channel.basicPublish("", queue,
                            new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
                            oldToken.getBytes(StandardCharsets.UTF_8));
                    channel.waitForConfirmsOrDie(5000);
                    GetResponse delivered = channel.basicGet(queue, false);
                    assertNotNull(delivered);
                    assertEquals(Result.DENIED, gate.admit(
                            new String(delivered.getBody(), StandardCharsets.UTF_8)));
                    channel.basicReject(delivered.getEnvelope().getDeliveryTag(), false);
                    GetResponse quarantined = null;
                    for (int n=0; n<30 && quarantined==null; n++) {
                        quarantined = channel.basicGet(dlq, false);
                        if (quarantined==null) Thread.sleep(100);
                    }
                    assertNotNull(quarantined);
                    assertArrayEquals(delivered.getBody(), quarantined.getBody());
                    channel.basicAck(quarantined.getEnvelope().getDeliveryTag(), false);
                } finally {
                    channel.queueDelete(queue);
                    channel.queueDelete(dlq);
                    channel.exchangeDelete(exchange);
                }
            }
            assertEquals(2, audit.queryForObject(
                    "SELECT count(*) FROM public.xerj_review_witness_effects",
                    Integer.class));
            System.out.println("XERJ_SECOND_CLUSTER_EPOCH_WITNESS_ROLLBACK_DENY_PASS");
        } finally {
            audit.execute("DROP TABLE IF EXISTS public.xerj_review_witness_effects");
            audit.execute("DROP TABLE IF EXISTS public.xerj_review_audit_epoch");
        }
    }
}
