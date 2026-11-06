package io.aetheris.users.events;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs ONLY after actual Tomcat Audit listener is ready on a new, restricted
 * disposable RabbitMQ 4 vhost. Exercises a real broker NACK->DLX->DLQ path.
 * Never prints or uploads payloads, passwords, or PII.
 */
class RabbitBrokerPoisonDeadLetterIT {
    private static final String SOURCE = "aetheris.audit.user-events";
    private static final String DLQ = "aetheris.audit.poison";
    private static final String EXCHANGE = "aetheris.events";
    private static final String VHOST = "xerj_review";
    private static final String POISON_ID = "xerj-ci-poison-tenant-20261106";

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("CI fixture missing: " + key);
        return value;
    }

    private static ConnectionFactory factory(String user, String password) {
        ConnectionFactory f = new ConnectionFactory();
        f.setHost("127.0.0.1");
        f.setPort(55672);
        f.setVirtualHost(VHOST);
        f.setUsername(user);
        f.setPassword(password);
        f.setConnectionTimeout(5000);
        f.setHandshakeTimeout(5000);
        return f;
    }

    @Test
    void rejectedTenantEventIsQuarantinedOnceWithoutQueueArgumentMigration() throws Exception {
        required("RUNNER_TEMP");
        String project = required("XERJ_COMPOSE_PROJECT");
        assertTrue(project.startsWith("xerj-mtls-ci-"), "Must use a disposable broker namespace");
        String publisherPassword = required("XERJ_BROKER_PUBLISH_PASSWORD");
        String bootstrapPassword = required("XERJ_TEST_RABBIT_PASSWORD");

        // The durable source queue must remain redeclarable with *no* x-arguments.
        try (Connection admin = factory("xerj_ci", bootstrapPassword).newConnection();
             Channel channel = admin.createChannel()) {
            channel.queueDeclare(SOURCE, true, false, false, null);
            channel.queueDeclarePassive(DLQ);
            assertNull(channel.basicGet(DLQ, true), "Quarantine must start empty");
        }

        String body = """
                {"eventId":"xerj-ci-poison-tenant-20261106",
                 "eventType":"user.created",
                 "occurredAt":"2026-11-06T06:00:00Z",
                 "userId":71197,
                 "name":"Synthetic poison fixture",
                 "email":"synthetic-poison@example.test",
                 "tenantId":"forged-not-a-uuid"}
                """;

        try (Connection publisher = factory("xerj_publisher", publisherPassword).newConnection();
             Channel channel = publisher.createChannel()) {
            channel.confirmSelect();
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .contentEncoding("utf-8")
                    .deliveryMode(2)
                    .headers(Map.of("__TypeId__", "java.util.LinkedHashMap"))
                    .build();
            channel.basicPublish(EXCHANGE, "user.created", props,
                    body.getBytes(StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(5000L);
        }

        try (Connection admin = factory("xerj_ci", bootstrapPassword).newConnection();
             Channel channel = admin.createChannel()) {
            GetResponse poison = null;
            for (int attempt = 0; attempt < 48 && poison == null; ++attempt) {
                poison = channel.basicGet(DLQ, true);
                if (poison == null) Thread.sleep(500);
            }
            assertNotNull(poison, "Real Audit listener must reject and broker must quarantine malformed tenant ID");
            assertEquals("aetheris.audit.dlx", poison.getEnvelope().getExchange());
            assertEquals("quarantine", poison.getEnvelope().getRoutingKey());
            assertTrue(new String(poison.getBody(), StandardCharsets.UTF_8).contains(POISON_ID));
            Map<String, Object> headers = poison.getProps().getHeaders();
            assertNotNull(headers, "Dead letter metadata must be present");
            assertEquals("rejected", String.valueOf(headers.get("x-first-death-reason")));
            assertEquals(SOURCE, String.valueOf(headers.get("x-first-death-queue")));
            assertNull(channel.basicGet(DLQ, true), "One poison publish must result in one quarantine delivery");
            System.out.println("XERJ_REAL_RABBITMQ_POISON_NACK_TO_OPERATOR_DLQ_PASS");
        }
    }
}
