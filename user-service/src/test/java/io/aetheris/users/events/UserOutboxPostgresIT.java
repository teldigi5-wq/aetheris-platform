package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only with the explicitly provisioned, disposable PostgreSQL in CI. */
class UserOutboxPostgresIT {
    private String needed(String key) {
        String value=System.getenv(key);
        if(value==null||value.isBlank()) throw new IllegalStateException("Missing synthetic DB fixture");
        return value;
    }

    @Test
    void outboxInsertRollsBackWithSameJdbcTransactionAndReplaysStableEventId() {
        assertTrue(needed("XERJ_COMPOSE_PROJECT").startsWith("xerj-mtls-ci-"));
        PGSimpleDataSource source=new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://127.0.0.1:55432/xerj_ci");
        source.setUser("xerj_ci");
        source.setPassword(needed("XERJ_TEST_PG_PASSWORD"));
        JdbcTemplate jdbc=new JdbcTemplate(source);
        UserOutboxStore store=new UserOutboxStore(jdbc);
        store.validateSchema();
        TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));

        assertThrows(IllegalStateException.class,
                () -> store.append("user.created",123L,"No Transaction","never@example.test",null));

        UUID[] rolledBack={null};
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            rolledBack[0]=store.append("user.created",123L,"Rolled Back",
                    "rollback@example.test",null);
            throw new IllegalStateException("simulated SQL transaction abort");
        }));
        assertNotNull(rolledBack[0]);
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM user_event_outbox WHERE event_id=?",
                Long.class,rolledBack[0]));

        UUID committed=tx.execute(status -> store.append("user.created",124L,
                "Committed","committed@example.test",null));
        assertNotNull(committed);
        try {
            var first=store.leaseNext().orElseThrow();
            assertEquals(committed,first.eventId());
            assertEquals(1,first.attempts());
            // Simulate crash after broker publish but before marking receipt.
            jdbc.update("UPDATE user_event_outbox SET lease_until=clock_timestamp()-INTERVAL '1 second' WHERE event_id=?",committed);
            var replay=store.leaseNext().orElseThrow();
            assertEquals(first.eventId(),replay.eventId());
            assertNotEquals(first.leaseToken(),replay.leaseToken());
            assertEquals(2,replay.attempts());
            assertFalse(store.markDelivered(first),"Old lease cannot falsely ACK new claim");
            assertTrue(store.markDelivered(replay));
            assertEquals("delivered",jdbc.queryForObject(
                    "SELECT state FROM user_event_outbox WHERE event_id=?",
                    String.class,committed));
            assertFalse(store.markDelivered(replay),"Duplicate ACK cannot advance state");
        } finally {
            jdbc.update("DELETE FROM user_event_outbox WHERE event_id=?",committed);
        }
        System.out.println("XERJ_REAL_POSTGRES_OUTBOX_ROLLBACK_LEASE_REPLAY_PASS");
    }
}
