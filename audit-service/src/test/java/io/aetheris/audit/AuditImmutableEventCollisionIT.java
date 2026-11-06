package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL16 duplicate event-ID semantic integrity, isolated CI only. */
class AuditImmutableEventCollisionIT {
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing disposable CI fixture");
        return value;
    }

    private static JdbcTemplate db() {
        String run = required("GITHUB_RUN_ID");
        String attempt = required("GITHUB_RUN_ATTEMPT");
        if (!"true".equals(required("GITHUB_ACTIONS"))
                || !run.matches("[0-9]+") || !attempt.matches("[0-9]+")
                || !required("XERJ_COMPOSE_PROJECT").equals("xerj-mtls-ci-" + run + "-" + attempt)
                || !required("RUNNER_TEMP").startsWith("/home/runner/")) {
            throw new IllegalStateException("Not the expected ephemeral runner");
        }
        PGSimpleDataSource data = new PGSimpleDataSource();
        data.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_fence_audit"
                + "?connectTimeout=2&socketTimeout=4");
        data.setUser("xerj_ci");
        data.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        return new JdbcTemplate(data);
    }

    @Test
    void exactDuplicatesAreIdempotentButTenantAndContentCollisionsAreRejected()
            throws Exception {
        JdbcTemplate jdbc = db();
        assertEquals("88888888-8888-4888-8888-888888888888",
                jdbc.queryForObject("SELECT token::text FROM public.xerj_dr_fence",
                        String.class));
        var repo = new AuditEventRepository(jdbc);
        String id = UUID.randomUUID().toString();
        UUID tenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID another = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        Instant occurred = Instant.parse("2026-11-06T10:04:03.123456Z");
        var original = new UserDomainEvent(
                id, "user.created", occurred, 203L,
                "Synthetic Account", "synthetic@example.test", tenant);
        try {
            repo.saveIfAbsent(original);
            repo.saveIfAbsent(original);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                    Integer.class, id));

            for (UserDomainEvent forged : new UserDomainEvent[]{
                    new UserDomainEvent(id, original.eventType(), occurred, 203L,
                            original.name(), original.email(), another),
                    new UserDomainEvent(id, original.eventType(), occurred, 203L,
                            original.name(), original.email(), null),
                    new UserDomainEvent(id, "user.deleted", occurred, 203L,
                            original.name(), original.email(), tenant),
                    new UserDomainEvent(id, original.eventType(), occurred, 204L,
                            original.name(), original.email(), tenant),
                    new UserDomainEvent(id, original.eventType(), occurred, 203L,
                            "Forged", original.email(), tenant),
                    new UserDomainEvent(id, original.eventType(), occurred, 203L,
                            original.name(), "other@example.test", tenant),
                    new UserDomainEvent(id, original.eventType(), occurred.plusSeconds(60), 203L,
                            original.name(), original.email(), tenant)
            }) {
                var denial = assertThrows(AmqpRejectAndDontRequeueException.class,
                        () -> repo.saveIfAbsent(forged));
                assertEquals("Conflicting immutable Audit event identifier", denial.getMessage());
                assertNull(denial.getCause());
            }

            assertEquals(tenant.toString(), jdbc.queryForObject(
                    "SELECT tenant_id::text FROM public.audit_events WHERE event_id=?",
                    String.class, id));
            assertEquals("Synthetic Account", jdbc.queryForObject(
                    "SELECT name FROM public.audit_events WHERE event_id=?",
                    String.class, id));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                    Integer.class, id));
            System.out.println("XERJ_AUDIT_IMMUTABLE_EVENT_ID_COLLISION_DENIAL_PASS");
        } finally {
            jdbc.update("DELETE FROM public.audit_events WHERE event_id=?", id);
        }
    }

    @Test
    void historicalNoTenantDuplicateIsStillIdempotent() {
        JdbcTemplate jdbc = db();
        var repo = new AuditEventRepository(jdbc);
        String id = UUID.randomUUID().toString();
        var historic = new UserDomainEvent(id, "USER_DELETED",
                Instant.parse("2026-09-21T10:00:00Z"), 16L,
                "Historic", "history@example.test", null);
        try {
            repo.saveIfAbsent(historic);
            repo.saveIfAbsent(historic);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM public.audit_events WHERE event_id=?",
                    Integer.class, id));
            assertThrows(AmqpRejectAndDontRequeueException.class,
                    () -> repo.saveIfAbsent(new UserDomainEvent(
                            id, historic.eventType(), historic.occurredAt(),
                            historic.userId(), historic.name(), historic.email(),
                            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))));
        } finally {
            jdbc.update("DELETE FROM public.audit_events WHERE event_id=?", id);
        }
    }
}
