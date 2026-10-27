package io.aetheris.audit;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Genuine *concurrent* Flyway calls from independent instances against
 * the same initially empty synthetic schema. No Spring context or remote DB
 * discovery. CI explicitly supplies a disposable database and opts in.
 * This does NOT prove full application service-boot concurrency.
 */
@EnabledIfSystemProperty(named = "aetheris.audit.concurrent.synthetic", matches = "true")
class ConcurrentAuditFlywayMigrationTest {
    private static final String SCHEMA = "audit_race";

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing explicitly configured disposable DB environment: " + key);
        }
        return value;
    }

    private static Flyway newFlyway(String url, String user, String password) {
        return Flyway.configure()
            .dataSource(url, user, password)
            .schemas(SCHEMA)
            .defaultSchema(SCHEMA)
            .table("flyway_audit_schema_history")
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .cleanDisabled(true)
            .validateOnMigrate(true)
            .load();
    }

    private static int count(Connection connection, String query) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet results = statement.executeQuery()) {
            assertTrue(results.next());
            return results.getInt(1);
        }
    }

    @Test
    void twoIndependentMigratorsSafelySerializeOnOneAuditHistory() throws Exception {
        final String url = required("SPRING_DATASOURCE_URL");
        final String username = required("SPRING_DATASOURCE_USERNAME");
        final String password = required("SPRING_DATASOURCE_PASSWORD");
        assertTrue(url.startsWith("jdbc:postgresql://127.0.0.1:55436/shared_synthetic?currentSchema=audit_race"),
            "No non-disposable database targets allowed in concurrency rehearsal");
        assertEquals("audit_migrator", username);

        assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<MigrateResult> first = pool.submit(() -> {
                    start.await();
                    return newFlyway(url, username, password).migrate();
                });
                Future<MigrateResult> second = pool.submit(() -> {
                    start.await();
                    return newFlyway(url, username, password).migrate();
                });
                start.countDown();
                int executions = first.get(70, TimeUnit.SECONDS).migrationsExecuted
                    + second.get(70, TimeUnit.SECONDS).migrationsExecuted;
                assertEquals(1, executions, "Exactly one migrator must apply Audit V1");
                try (Connection conn = DriverManager.getConnection(url, username, password)) {
                    assertEquals(1, count(conn,
                        "SELECT count(*) FROM audit_race.flyway_audit_schema_history WHERE version='1' AND success"));
                    assertEquals(1, count(conn,
                        "SELECT count(*) FROM audit_race.flyway_audit_schema_history"));
                    assertEquals(1, count(conn,
                        "SELECT count(*) FROM information_schema.tables WHERE table_schema='audit_race' AND table_name='audit_events'"));
                }
                // A third fresh Flyway instance must not apply a duplicate.
                assertEquals(0, newFlyway(url, username, password).migrate().migrationsExecuted);
            } finally {
                start.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS),
                    "Rehearsal threads must terminate before CI cleanup");
            }
        });
    }
}
