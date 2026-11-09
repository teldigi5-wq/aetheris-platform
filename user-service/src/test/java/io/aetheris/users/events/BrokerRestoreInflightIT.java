package io.aetheris.users.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REVIEW-ONLY: real disposable RabbitMQ in-flight delivery vs three independent
 * synthetic PostgreSQL recovery-fence databases created by the preceding CI step.
 * This is a necessary negative promotion gate, not an operational recovery protocol.
 */
class BrokerRestoreInflightIT {
    private static final String EVENT = "66666666-6666-4666-8666-666666666666";
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String FENCE = "88888888-8888-4888-8888-888888888888";
    private static final String COMPOSE_FILE = "tests/security/xerj-disposable-deps.compose.yml";

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing ephemeral CI fixture: " + name);
        return value;
    }

    private static String confinedProject() {
        if (!"true".equals(required("GITHUB_ACTIONS"))) {
            throw new IllegalStateException("Only hosted isolated CI is permitted");
        }
        String run = required("GITHUB_RUN_ID");
        String attempt = required("GITHUB_RUN_ATTEMPT");
        String project = required("XERJ_COMPOSE_PROJECT");
        if (!run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !project.equals("xerj-mtls-ci-" + run + "-" + attempt)
                || !required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Refusing non-disposable review project");
        }
        return project;
    }

    private static JdbcTemplate db(String name) {
        if (!name.equals("xerj_fence_identity")
                && !name.equals("xerj_fence_user")
                && !name.equals("xerj_fence_audit")
                && !name.equals("xerj_fence_audit_mixed")
                && !name.equals("xerj_fence_audit_r")) {
            throw new IllegalArgumentException("Unexpected synthetic database");
        }
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:55432/" + name);
        ds.setUser("xerj_ci");
        ds.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(ds);
    }

    private static String scalar(JdbcTemplate jdbc, String query, Object... params) {
        return jdbc.queryForObject(query, String.class, params);
    }

    private static boolean databasesCoherent(String auditDatabase) {
        // Real DB queries: independently restored and even internally valid
        // databases must agree on a fence, authoritative tenant and event.
        try {
            JdbcTemplate identity = db("xerj_fence_identity");
            JdbcTemplate user = db("xerj_fence_user");
            JdbcTemplate audit = db(auditDatabase);
            for (JdbcTemplate jdbc : new JdbcTemplate[]{identity, user, audit}) {
                if (!FENCE.equals(scalar(jdbc, "SELECT token::text FROM xerj_dr_fence"))) return false;
            }
            if (!TENANT.equals(scalar(identity,
                    "SELECT tenant_id::text FROM identity_accounts WHERE id=101"))) return false;
            if (!TENANT.equals(scalar(user,
                    "SELECT tenant_id::text FROM users WHERE id=203"))) return false;
            if (!TENANT.equals(scalar(user,
                    "SELECT tenant_id::text FROM user_event_outbox WHERE event_id=?",
                    UUID.fromString(EVENT)))) return false;
            if (!"delivered".equals(scalar(user,
                    "SELECT state FROM user_event_outbox WHERE event_id=?",
                    UUID.fromString(EVENT)))) return false;
            if (!TENANT.equals(scalar(audit,
                    "SELECT tenant_id::text FROM audit_events WHERE event_id=?", EVENT))) return false;
            Integer count = audit.queryForObject(
                    "SELECT count(*) FROM audit_events WHERE event_id=?", Integer.class, EVENT);
            return count != null && count == 1;
        } catch (DataAccessException failure) {
            // Missing data, an unavailable database and conflicting restore
            // metadata all deny release. Do not expose payloads in CI output.
            return false;
        }
    }

    private record QueueCounts(int ready, int unacked) {}

    private static QueueCounts queueCounts(String project, String queue) throws Exception {
        // Read exact QUEUE-level state from the disposable broker itself.
        // AMQP queueDeclarePassive only reports ready count, not unacked.
        Process process = new ProcessBuilder(
                "docker", "compose", "-p", project, "-f", COMPOSE_FILE,
                "exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues",
                "-p", "xerj_review", "name", "messages_ready", "messages_unacknowledged")
                .redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Broker witness timed out");
        }
        if (process.exitValue() != 0) throw new IllegalStateException("Broker witness unavailable");
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String row : output.split("\\R")) {
            String[] fields = row.trim().split("\\s+");
            if (fields.length == 3 && queue.equals(fields[0])) {
                return new QueueCounts(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
            }
        }
        throw new IllegalStateException("Synthetic queue missing from broker witness");
    }

    private static QueueCounts awaitCounts(String project, String queue, int ready, int unacked)
            throws Exception {
        QueueCounts last = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            last = queueCounts(project, queue);
            if (last.ready() == ready && last.unacked() == unacked) return last;
            Thread.sleep(200);
        }
        fail("RabbitMQ did not reach the expected isolated queue state");
        return last;
    }

    private static boolean eligible(String project, String queue, String auditDatabase)
            throws Exception {
        QueueCounts state = queueCounts(project, queue);
        return databasesCoherent(auditDatabase) && state.ready() == 0 && state.unacked() == 0;
    }

    private static void publish(Channel channel, String queue, byte[] bytes) throws Exception {
        channel.confirmSelect();
        channel.basicPublish("", queue,
                new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .build(), bytes);
        channel.waitForConfirmsOrDie(5_000);
    }

    @Test
    void interruptedConsumerRequeuesWhileDatabaseRestoreFenceMustRemainOnHold() throws Exception {
        String project = confinedProject();
        assertTrue(databasesCoherent("xerj_fence_audit"));
        assertFalse(databasesCoherent("xerj_fence_audit_mixed"));
        assertFalse(databasesCoherent("xerj_fence_audit_r"));

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(55672);
        factory.setVirtualHost("xerj_review");
        // Ephemeral CI fixture administrator only; no production identity or ACL change.
        factory.setUsername("xerj_ci");
        factory.setPassword(required("XERJ_TEST_RABBIT_PASSWORD"));
        factory.setConnectionTimeout(5000);
        String queue = "xerj.recovery.inflight." + UUID.randomUUID();
        byte[] original = new ObjectMapper().writeValueAsBytes(Map.of(
                "eventId", EVENT, "eventType", "user.created",
                "tenantId", TENANT, "userId", 203,
                "name", "Synthetic B", "email", "fence-b-user@example.test",
                "occurredAt", "2026-11-06T00:00:00Z"));

        try (Connection connection = factory.newConnection();
             Channel owner = connection.createChannel()) {
            owner.queueDeclare(queue, true, false, false, Map.of("x-expires", 300_000L));
            try {
                assertTrue(eligible(project, queue, "xerj_fence_audit"));
                try (Channel first = connection.createChannel()) {
                    publish(first, queue, original);
                    GetResponse held = first.basicGet(queue, false);
                    assertNotNull(held, "Persistent broker message must be deliverable");
                    awaitCounts(project, queue, 0, 1);
                    assertFalse(eligible(project, queue, "xerj_fence_audit"),
                            "DB fence matches, but a consumer holds an unacknowledged message");

                    // Audit commit has already occurred; attempting it again must
                    // deduplicate by stable event ID, never insert a second row.
                    JdbcTemplate audit = db("xerj_fence_audit");
                    assertEquals(0, audit.update(
                            "INSERT INTO audit_events " +
                            "(event_id,event_type,occurred_at,user_id,name,email,tenant_id) " +
                            "VALUES (?,?,?,?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
                            EVENT, "user.created", Timestamp.from(Instant.parse("2026-11-06T00:00:00Z")),
                            203L, "Synthetic B", "fence-b-user@example.test", UUID.fromString(TENANT)));
                    assertEquals(1, audit.queryForObject(
                            "SELECT count(*) FROM audit_events WHERE event_id=?", Integer.class, EVENT));
                    // Intentional simulated process death BEFORE manual RabbitMQ ACK.
                }

                // Channel loss must requeue the committed-but-unacked delivery.
                awaitCounts(project, queue, 1, 0);
                assertFalse(eligible(project, queue, "xerj_fence_audit"),
                        "Ready redelivery must also block restored database promotion");

                try (Channel resumed = connection.createChannel()) {
                    GetResponse replay = resumed.basicGet(queue, false);
                    assertNotNull(replay);
                    assertTrue(replay.getEnvelope().isRedeliver());
                    assertArrayEquals(original, replay.getBody(), "Message identity must survive broker redelivery");
                    awaitCounts(project, queue, 0, 1);
                    assertFalse(eligible(project, queue, "xerj_fence_audit"));
                    resumed.basicAck(replay.getEnvelope().getDeliveryTag(), false);
                }
                awaitCounts(project, queue, 0, 0);
                assertTrue(eligible(project, queue, "xerj_fence_audit"),
                        "Only this known synthetic queue is now drained; NOT a real promotion approval");
                assertFalse(eligible(project, queue, "xerj_fence_audit_mixed"));
                assertFalse(eligible(project, queue, "xerj_fence_audit_r"));

                // A LATE durable replay can arrive even after an empty-queue
                // observation; the gate must be rechecked at the cutover boundary.
                try (Channel late = connection.createChannel()) {
                    publish(late, queue, original);
                    awaitCounts(project, queue, 1, 0);
                    assertFalse(eligible(project, queue, "xerj_fence_audit"));
                    GetResponse delayed = late.basicGet(queue, false);
                    assertNotNull(delayed);
                    late.basicAck(delayed.getEnvelope().getDeliveryTag(), false);
                }
                awaitCounts(project, queue, 0, 0);
                System.out.println("XERJ_BROKER_INFLIGHT_REDELIVERY_RECOVERY_HOLD_PASS");
            } finally {
                owner.queueDelete(queue);
            }
        }
    }
}
