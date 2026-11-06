package io.aetheris.users.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable RabbitMQ vhost inventory and recovery-fence NEGATIVE proof.
 * No production admission code, migration, broker policy, credentials or
 * privileged broker mutation is created by this review-only test.
 */
class BrokerVhostRecoveryCustodyIT {
    private static final String COMPOSE_FILE = "tests/security/xerj-disposable-deps.compose.yml";
    private static final String VHOST = "xerj_review";
    private static final String PRIMARY_QUEUE = "aetheris.audit.user-events";
    private static final String POISON_QUEUE = "aetheris.audit.poison";
    private static final String FENCE_B = "88888888-8888-4888-8888-888888888888";
    private static final String FENCE_A = "77777777-7777-4777-8777-777777777777";
    private static final String EVENT_B = "66666666-6666-4666-8666-666666666666";
    private static final String TENANT = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    private static String mustEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing disposable CI fixture: " + name);
        }
        return value;
    }

    private static String isolatedProject() {
        String run = mustEnv("GITHUB_RUN_ID");
        String attempt = mustEnv("GITHUB_RUN_ATTEMPT");
        String project = mustEnv("XERJ_COMPOSE_PROJECT");
        String tmp = mustEnv("RUNNER_TEMP");
        if (!"true".equals(mustEnv("GITHUB_ACTIONS"))
                || !run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !project.equals("xerj-mtls-ci-" + run + "-" + attempt)
                || !tmp.startsWith("/home/runner/")) {
            throw new IllegalStateException("Outside exact isolated hosted CI project");
        }
        return project;
    }

    private record QueueState(int ready, int unacknowledged, int consumers) {
        boolean idle() {
            return ready == 0 && unacknowledged == 0 && consumers == 0;
        }
    }

    /**
     * Inventory every queue in the disposable, explicitly selected vhost,
     * including the normally operator-only DLQ. This is NOT broker-wide
     * inventory across all vhosts, replicas, streams or delayed exchanges.
     */
    private static Map<String, QueueState> inventory(String project) throws Exception {
        Process process = new ProcessBuilder(
                "docker", "compose", "-p", project, "-f", COMPOSE_FILE,
                "exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues",
                "-p", VHOST, "name", "messages_ready", "messages_unacknowledged", "consumers")
                .redirectErrorStream(true).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("RabbitMQ inventory request timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("RabbitMQ inventory not available");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, QueueState> queues = new HashMap<>();
        for (String raw : output.split("\\R")) {
            String row = raw.trim();
            if (row.isEmpty() || row.startsWith("Listing queues")
                    || row.startsWith("Timeout:")
                    || row.startsWith("name\t")
                    || row.equals("name messages_ready messages_unacknowledged consumers")) {
                continue;
            }
            String[] fields = row.split("\\s+");
            if (fields.length != 4
                    || !fields[1].matches("[0-9]+")
                    || !fields[2].matches("[0-9]+")
                    || !fields[3].matches("[0-9]+")) {
                // Unexpected CLI output is not evidence of a complete inventory.
                throw new IllegalStateException("Unrecognized broker inventory format");
            }
            QueueState prior = queues.putIfAbsent(fields[0], new QueueState(
                    Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                    Integer.parseInt(fields[3])));
            if (prior != null) {
                throw new IllegalStateException("Duplicate queue inventory entry");
            }
        }
        if (queues.isEmpty()) {
            throw new IllegalStateException("Broker returned no expected queues");
        }
        return Map.copyOf(queues);
    }

    private static JdbcTemplate database(String name) {
        if (!Set.of("xerj_fence_identity", "xerj_fence_user", "xerj_fence_audit").contains(name)) {
            throw new IllegalArgumentException("Unexpected database target");
        }
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL("jdbc:postgresql://127.0.0.1:55432/" + name);
        ds.setUser("xerj_ci");
        ds.setPassword(mustEnv("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(ds);
    }

    private static boolean matchedSyntheticRecoveryFence() {
        try {
            JdbcTemplate identity = database("xerj_fence_identity");
            JdbcTemplate user = database("xerj_fence_user");
            JdbcTemplate audit = database("xerj_fence_audit");
            for (JdbcTemplate jdbc : new JdbcTemplate[]{identity, user, audit}) {
                if (!FENCE_B.equals(jdbc.queryForObject(
                        "SELECT token::text FROM xerj_dr_fence", String.class))) {
                    return false;
                }
            }
            if (!TENANT.equals(identity.queryForObject(
                    "SELECT tenant_id::text FROM identity_accounts WHERE id=101", String.class))) {
                return false;
            }
            if (!TENANT.equals(user.queryForObject(
                    "SELECT tenant_id::text FROM users WHERE id=203", String.class))) {
                return false;
            }
            if (!"delivered".equals(user.queryForObject(
                    "SELECT state FROM user_event_outbox WHERE event_id=?",
                    String.class, UUID.fromString(EVENT_B)))) {
                return false;
            }
            if (!TENANT.equals(audit.queryForObject(
                    "SELECT tenant_id::text FROM audit_events WHERE event_id=?",
                    String.class, EVENT_B))) {
                return false;
            }
            Integer receipt = audit.queryForObject(
                    "SELECT COUNT(*) FROM audit_events WHERE event_id=?",
                    Integer.class, EVENT_B);
            return receipt != null && receipt == 1;
        } catch (DataAccessException failure) {
            return false;
        }
    }

    private static boolean reviewGate(String project, Set<String> expectedQueues) {
        try {
            // Re-read broker inventory at each decision point. No cached
            // "queue empty" observation is accepted as a cutover capability.
            Map<String, QueueState> queues = inventory(project);
            if (!queues.keySet().equals(expectedQueues) || !matchedSyntheticRecoveryFence()) {
                return false;
            }
            return queues.values().stream().allMatch(QueueState::idle);
        } catch (Exception unsafe) {
            return false;
        }
    }

    private static void awaitState(String project, String queue,
                                   int ready, int unacked, int consumers) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            QueueState state = inventory(project).get(queue);
            if (state != null && state.ready() == ready
                    && state.unacknowledged() == unacked && state.consumers() == consumers) {
                return;
            }
            Thread.sleep(250);
        }
        fail("Disposable broker queue did not enter expected custody state");
    }

    private static void publish(Channel channel, String queue, byte[] body) throws Exception {
        channel.confirmSelect();
        channel.basicPublish("", queue,
                new AMQP.BasicProperties.Builder()
                        .deliveryMode(2).contentType("application/octet-stream").build(),
                body);
        channel.waitForConfirmsOrDie(5000);
    }

    @Test
    void wholeVhostInventoryDeniesUnknownQueueDeadLetterConsumerAndStaleFence()
            throws Exception {
        String project = isolatedProject();
        Set<String> base = Set.of(PRIMARY_QUEUE, POISON_QUEUE);
        assertTrue(matchedSyntheticRecoveryFence(), "Prior CI database restore fixture absent");
        assertTrue(reviewGate(project, base), "Broker source and DLQ must begin empty");

        String allowedQueue = "xerj.recovery.custody." + UUID.randomUUID();
        String unexpectedQueue = "xerj.recovery.unknown." + UUID.randomUUID();
        byte[] fixture = ("synthetic-custody-" + UUID.randomUUID())
                .getBytes(StandardCharsets.UTF_8);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("127.0.0.1");
        factory.setPort(55672);
        factory.setVirtualHost(VHOST);
        factory.setUsername("xerj_ci");
        // Only ephemeral test-only broker administrator creates/deletes queues.
        factory.setPassword(mustEnv("XERJ_TEST_RABBIT_PASSWORD"));
        factory.setConnectionTimeout(5000);

        try (Connection conn = factory.newConnection();
             Channel topology = conn.createChannel()) {
            topology.queueDeclare(allowedQueue, true, false, false,
                    Map.of("x-expires", 300_000L));
            Set<String> expected = Set.of(PRIMARY_QUEUE, POISON_QUEUE, allowedQueue);
            try {
                awaitState(project, allowedQueue, 0, 0, 0);
                assertTrue(reviewGate(project, expected));

                // An active consumer can receive a message AFTER the inventory
                // reads "empty": consumer registration itself blocks quiescence.
                try (Channel active = conn.createChannel()) {
                    DeliverCallback delivery = (tag, msg) -> {};
                    String consumerTag = active.basicConsume(
                            allowedQueue, false, delivery, tag -> {});
                    awaitState(project, allowedQueue, 0, 0, 1);
                    assertFalse(reviewGate(project, expected));
                    active.basicCancel(consumerTag);
                }
                awaitState(project, allowedQueue, 0, 0, 0);
                assertTrue(reviewGate(project, expected));

                // An operator-only poison queue is part of the custody boundary;
                // source queue emptiness must never hide it.
                try (Channel poison = conn.createChannel()) {
                    publish(poison, POISON_QUEUE, fixture);
                    awaitState(project, POISON_QUEUE, 1, 0, 0);
                    assertFalse(reviewGate(project, expected));
                    GetResponse held = poison.basicGet(POISON_QUEUE, false);
                    assertNotNull(held);
                    assertArrayEquals(fixture, held.getBody());
                    awaitState(project, POISON_QUEUE, 0, 1, 0);
                    assertFalse(reviewGate(project, expected));
                    poison.basicAck(held.getEnvelope().getDeliveryTag(), false);
                }
                awaitState(project, POISON_QUEUE, 0, 0, 0);
                assertTrue(reviewGate(project, expected));

                // An unregistered queue, even empty, invalidates a supposedly
                // complete inventory rather than being silently ignored.
                topology.queueDeclare(unexpectedQueue, true, false, false,
                        Map.of("x-expires", 300_000L));
                try {
                    awaitState(project, unexpectedQueue, 0, 0, 0);
                    assertFalse(reviewGate(project, expected));
                } finally {
                    topology.queueDelete(unexpectedQueue);
                }
                assertTrue(reviewGate(project, expected));

                // Revalidate at decision time: a late broker publish falsifies
                // a formerly positive clean-queue inventory.
                Map<String, QueueState> staleObservation = inventory(project);
                assertEquals(expected, staleObservation.keySet());
                assertTrue(staleObservation.values().stream().allMatch(QueueState::idle));
                try (Channel late = conn.createChannel()) {
                    publish(late, allowedQueue, fixture);
                    awaitState(project, allowedQueue, 1, 0, 0);
                    assertFalse(reviewGate(project, expected));
                    GetResponse pending = late.basicGet(allowedQueue, false);
                    assertNotNull(pending);
                    awaitState(project, allowedQueue, 0, 1, 0);
                    assertFalse(reviewGate(project, expected));
                    late.basicAck(pending.getEnvelope().getDeliveryTag(), false);
                }
                awaitState(project, allowedQueue, 0, 0, 0);
                assertTrue(reviewGate(project, expected));

                // Database snapshots must also match the fixed generation marker.
                // A mutable test marker is NOT an independent monotonic witness.
                JdbcTemplate audit = database("xerj_fence_audit");
                try {
                    assertEquals(1, audit.update(
                            "UPDATE xerj_dr_fence SET token=?::uuid",
                            FENCE_A));
                    assertFalse(reviewGate(project, expected),
                            "Older Audit recovery fence must deny cutover");
                } finally {
                    audit.update("UPDATE xerj_dr_fence SET token=?::uuid", FENCE_B);
                }
                assertTrue(reviewGate(project, expected));

                System.out.println("XERJ_RABBITMQ_VHOST_ALL_QUEUE_CUSTODY_FAIL_CLOSED_PASS");
            } finally {
                topology.queueDelete(allowedQueue);
            }
        }
        assertTrue(reviewGate(project, base));
    }
}
