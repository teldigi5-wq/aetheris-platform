package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual PostgreSQL 16 concurrency, no H2/mock. Dedicated hosted synthetic DB
 * only; never runs against a real user database.
 */
class UserOutboxConcurrentPostgresIT {
    private static final int EVENTS = 24;
    private static final int WORKERS = 8;
    private static final long START_USER = 630000L;

    private String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing disposable CI fixture");
        return value;
    }

    @Test
    void eightIndependentRelayWorkersNeverClaimSamePendingEvent() throws Exception {
        String project = required("XERJ_COMPOSE_PROJECT");
        assertTrue(project.matches("xerj-mtls-ci-[0-9]+-[0-9]+"));
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_ci");
        source.setUser("xerj_ci");
        source.setPassword(required("XERJ_TEST_PG_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var store = new UserOutboxStore(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        Set<UUID> expected = new HashSet<>();
        try {
            for (int index = 0; index < EVENTS; index++) {
                final int n = index;
                UUID id = tx.execute(status -> store.append("user.created",
                        START_USER + n, "Synthetic worker stress",
                        "outbox-stress-" + n + "@example.test", null));
                assertNotNull(id);
                assertTrue(expected.add(id));
            }
            var seen = ConcurrentHashMap.<UUID>newKeySet();
            var leases = ConcurrentHashMap.<UUID>newKeySet();
            var start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(WORKERS);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
                for (int worker = 0; worker < WORKERS; worker++) {
                    futures.add(executor.submit((Callable<Integer>) () -> {
                        var independentStore = new UserOutboxStore(new JdbcTemplate(source));
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        int processed = 0;
                        while (true) {
                            var leased = independentStore.leaseNext();
                            if (leased.isEmpty()) break;
                            var event = leased.get();
                            assertTrue(expected.contains(event.eventId()), "Unexpected claimed event");
                            assertTrue(seen.add(event.eventId()), "Double-claim of a pending event");
                            assertTrue(leases.add(event.leaseToken()), "Duplicate lease token");
                            assertEquals(1, event.attempts());
                            // Encourage overlapping claims while preserving 30s lease.
                            Thread.sleep(8);
                            assertTrue(independentStore.markDelivered(event),
                                    "Current owner must be able to ACK own lease");
                            processed++;
                        }
                        return processed;
                    }));
                }
                start.countDown();
                int total = 0;
                for (var future : futures) total += future.get(45, TimeUnit.SECONDS);
                assertEquals(EVENTS, total);
                assertEquals(expected, seen);
                assertEquals(EVENTS, jdbc.queryForObject("""
                        SELECT COUNT(*) FROM user_event_outbox
                        WHERE user_id >= ? AND user_id < ? AND state='delivered'
                        """, Integer.class, START_USER, START_USER + EVENTS));
                assertEquals(0, jdbc.queryForObject("""
                        SELECT COUNT(*) FROM user_event_outbox
                        WHERE user_id >= ? AND user_id < ? AND state='leased'
                        """, Integer.class, START_USER, START_USER + EVENTS));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
            System.out.println("XERJ_REAL_POSTGRES_OUTBOX_EIGHT_WORKER_ATOMIC_CLAIM_PASS");
        } finally {
            jdbc.update("DELETE FROM user_event_outbox WHERE user_id >= ? AND user_id < ?",
                    START_USER, START_USER + EVENTS);
        }
    }
}
