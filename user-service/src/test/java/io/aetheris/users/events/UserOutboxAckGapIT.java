package io.aetheris.users.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Against REAL running disposable Audit consumer + User relay + PostgreSQL +
 * RabbitMQ. Simulates a process crash AFTER a confirmed publish but BEFORE
 * the SQL 'delivered' transition. A re-lease must send the stable event ID
 * again, and Audit must retain exactly ONE row (at-least-once transport).
 */
class UserOutboxAckGapIT {
    private static final long USER_ID = 631900L;

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing synthetic CI fixture: " + key);
        return value;
    }

    @Test
    void confirmedPublishThenProcessDeathReplaysSameEventIdButOneAuditRow() throws Exception {
        assertTrue(required("XERJ_COMPOSE_PROJECT").matches("xerj-mtls-ci-[0-9]+-[0-9]+"));
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_ci");
        source.setUser("xerj_ci");
        source.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var store = new UserOutboxStore(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        // Append AND reserve the first synthetic lease in ONE transaction:
        // the real scheduler must never see a brief committed pending row.
        UUID owner = UUID.randomUUID();
        UUID eventId = tx.execute(status -> {
            UUID id = store.append("user.created", USER_ID, "Synthetic ACK-gap case",
                    "ack-gap@example.test", null);
            assertEquals(1, jdbc.update("""
                    UPDATE user_event_outbox SET state='leased', attempts=1,
                    lease_token=?, lease_until=clock_timestamp()+INTERVAL '3 minutes'
                    WHERE event_id=? AND state='pending'
                    """, owner, id));
            return id;
        });
        assertNotNull(eventId);
        try {

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("127.0.0.1");
            factory.setPort(55672);
            factory.setVirtualHost("xerj_review");
            factory.setUsername("xerj_publisher");
            factory.setPassword(required("XERJ_BROKER_PUBLISH_PASSWORD"));
            factory.setConnectionTimeout(5000);
            var event = new LinkedHashMap<String, Object>();
            event.put("eventId", eventId.toString());
            event.put("eventType", "user.created");
            event.put("occurredAt", Instant.now().toString());
            event.put("userId", USER_ID);
            event.put("name", "Synthetic ACK-gap case");
            event.put("email", "ack-gap@example.test");
            byte[] bytes = new ObjectMapper().writeValueAsBytes(event);

            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {
                channel.confirmSelect();
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .headers(Map.of("__TypeId__", "java.util.LinkedHashMap"))
                        .build();
                channel.basicPublish(UserEventPublisher.EXCHANGE, "user.created",
                        props, bytes);
                channel.waitForConfirmsOrDie(5000);
            }

            // Confirmed at broker, not yet 'delivered' in outbox.
            assertEquals("leased", jdbc.queryForObject(
                    "SELECT state FROM user_event_outbox WHERE event_id=?",
                    String.class, eventId));
            boolean firstAudit = false;
            for (int attempt=0; attempt<50; ++attempt) {
                if (count(jdbc, eventId) == 1) {
                    firstAudit = true;
                    break;
                }
                Thread.sleep(200);
            }
            assertTrue(firstAudit, "Audit must persist first confirmed broker copy");

            // Simulated crash: lose the worker; force expiry on its unchanged
            // lease so actual running relay republishes the SAME event ID.
            assertEquals(1,jdbc.update("""
                    UPDATE user_event_outbox
                    SET lease_until=clock_timestamp()-INTERVAL '1 second'
                    WHERE event_id=? AND state='leased' AND lease_token=?
                    """,eventId,owner));
            boolean replayed = false;
            for (int attempt=0;attempt<100;++attempt) {
                String state=jdbc.queryForObject(
                        "SELECT state FROM user_event_outbox WHERE event_id=?",
                        String.class,eventId);
                Integer tries=jdbc.queryForObject(
                        "SELECT attempts FROM user_event_outbox WHERE event_id=?",
                        Integer.class,eventId);
                if ("delivered".equals(state) && tries != null && tries == 2) {
                    replayed=true;
                    break;
                }
                Thread.sleep(200);
            }
            assertTrue(replayed, "Actual User relay must reclaim after ACK-gap crash");
            // Audit event_id PK makes repeated broker messages idempotent.
            assertEquals(1, count(jdbc,eventId));
            System.out.println("XERJ_REAL_OUTBOX_ACK_GAP_REPLAY_AND_AUDIT_DEDUP_PASS");
        } finally {
            jdbc.update("DELETE FROM user_event_outbox WHERE event_id=?",eventId);
            jdbc.update("DELETE FROM audit_events WHERE event_id=?",eventId.toString());
        }
    }

    private static Integer count(JdbcTemplate jdbc, UUID id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE event_id=?",
                Integer.class,id.toString());
    }
}
