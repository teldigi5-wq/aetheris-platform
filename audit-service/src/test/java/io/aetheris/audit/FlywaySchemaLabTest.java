package io.aetheris.audit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Disposable Flyway integration lab; never an application startup migration.
 * All databases, account values and credentials are synthetic GitHub-runner fixtures.
 * No public methods here accept an arbitrary hostname or database name.
 */
class FlywaySchemaLabTest {
    private static final String EXPECTED_PREFIX = "jdbc:postgresql://127.0.0.1:15435/";
    private static final String HISTORY = "flyway_identity_user_ci_history";
    private static final String MODE = "CI_ONLY_SYNTHETIC";
    private static String password;

    @BeforeAll
    static void requireExplicitLabIsolation() {
        Assumptions.assumeTrue(MODE.equals(System.getenv("AETHERIS_SCHEMA_LAB_MODE")),
                "Only run via the isolated schema lab workflow");
        assertEquals(EXPECTED_PREFIX, System.getenv("AETHERIS_SCHEMA_LAB_JDBC_PREFIX"),
                "Refusing any PostgreSQL endpoint outside the disposable loopback port");
        password = System.getenv("AETHERIS_SCHEMA_LAB_PASSWORD");
        assertNotNull(password);
        assertTrue(password.length() >= 32);
    }

    private static String jdbc(String db) {
        if (!java.util.Set.of("lab_fresh", "lab_existing",
                "lab_drift", "lab_unmarked").contains(db)) {
            throw new IllegalArgumentException("Unexpected synthetic lab database");
        }
        return EXPECTED_PREFIX + db;
    }

    private static Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(jdbc(db), "postgres", password);
    }

    private static Flyway flyway(String db) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("docs/database/flyway-lab"))
                ? cwd : cwd.getParent();
        Path migrations = repo.resolve("docs/database/flyway-lab").normalize();
        assertTrue(Files.isDirectory(migrations), "Migrations must exist under docs only");
        return Flyway.configure()
                .dataSource(jdbc(db), "postgres", password)
                .locations("filesystem:" + migrations)
                .table(HISTORY)
                .baselineVersion("1")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load();
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql);
             ResultSet r = s.executeQuery()) {
            assertTrue(r.next());
            return r.getInt(1);
        }
    }

    private static void requireSyntheticLegacy(Connection c) throws SQLException {
        assertEquals(1, count(c,
                "SELECT count(*) FROM aetheris_schema_rehearsal_guard " +
                "WHERE marker='CI_ONLY_SYNTHETIC'"),
                "Refusing any database without test fixture marker");
        // This preflight happens before explicit Flyway.baseline(), so drift
        // cannot be accidentally relabeled as a supported historical schema.
        assertEquals(1, count(c,
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name='users' " +
                "AND column_name='email' AND data_type='character varying' " +
                "AND character_maximum_length=255 AND is_nullable='NO'"),
                "Refusing unexpected legacy User schema");
        assertEquals(1, count(c,
                "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema='public' AND table_name='identity_refresh_tokens' " +
                "AND column_name='token_hash' AND data_type='character varying' " +
                "AND character_maximum_length=64 AND is_nullable='NO'"),
                "Refusing unexpected legacy token schema");
        assertEquals(1, count(c,
                "SELECT count(*) FROM pg_constraint k " +
                "WHERE k.conrelid='public.identity_refresh_tokens'::regclass " +
                "AND k.contype='f' AND k.confrelid='public.identity_accounts'::regclass"),
                "Refusing missing refresh-token FK");
    }

    @Test
    void freshSchemaRunsTwoExplicitVersionedFlywayMigrations() throws Exception {
        try (Connection c = connect("lab_fresh")) {
            assertEquals(0, count(c,
                    "SELECT count(*) FROM pg_tables WHERE schemaname='public'"));
        }
        Flyway f = flyway("lab_fresh");
        MigrateResult first = f.migrate();
        assertEquals(2, first.migrationsExecuted);
        assertEquals(0, f.migrate().migrationsExecuted);
        try (Connection c = connect("lab_fresh")) {
            assertEquals(1, count(c,
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='public' " +
                    "AND indexname='idx_refresh_token_hash'"));
            assertEquals(2, count(c, "SELECT count(*) FROM " + HISTORY +
                    " WHERE success AND type='SQL'"));
            assertEquals(1, count(c,
                    "SELECT count(*) FROM aetheris_schema_rehearsal_guard " +
                    "WHERE marker='CI_ONLY_SYNTHETIC'"));
        }
    }

    @Test
    void prepopulatedSyntheticSchemaRequiresExplicitReviewedBaseline() throws Exception {
        try (Connection c = connect("lab_existing")) {
            requireSyntheticLegacy(c);
            assertEquals(1, count(c, "SELECT count(*) FROM identity_accounts"));
            assertEquals(1, count(c, "SELECT count(*) FROM identity_refresh_tokens"));
            assertEquals(1, count(c, "SELECT count(*) FROM users"));
        }
        Flyway f = flyway("lab_existing");
        assertNotNull(f.baseline(), "Explicit CI-only baseline required");
        assertEquals(1, f.migrate().migrationsExecuted); // Only guarded V2
        assertEquals(0, f.migrate().migrationsExecuted);
        try (Connection c = connect("lab_existing")) {
            assertEquals(1, count(c,
                    "SELECT count(*) FROM pg_indexes WHERE indexname='idx_refresh_token_hash'"));
            assertEquals(1, count(c,
                    "SELECT count(*) FROM " + HISTORY + " WHERE type='BASELINE'"));
            assertEquals(1, count(c,
                    "SELECT count(*) FROM " + HISTORY + " WHERE type='SQL' AND success"));
            assertEquals(1, count(c, "SELECT count(*) FROM identity_accounts"));
            assertEquals(1, count(c, "SELECT count(*) FROM identity_refresh_tokens"));
            assertEquals(1, count(c, "SELECT count(*) FROM users"));
        }
    }

    @Test
    void incompatibleLegacySchemaIsRejectedBeforeBaseline() throws Exception {
        try (Connection c = connect("lab_drift")) {
            assertThrows(AssertionError.class, () -> requireSyntheticLegacy(c));
            assertEquals(0, count(c,
                    "SELECT count(*) FROM pg_tables WHERE schemaname='public' " +
                    "AND tablename='" + HISTORY + "'"),
                    "Failed preflight must not create Flyway history");
            assertEquals(1, count(c, "SELECT count(*) FROM users"));
        }
    }

    @Test
    void unmarkedSchemaMustNeverBeBaselined() throws Exception {
        try (Connection c = connect("lab_unmarked")) {
            assertThrows(SQLException.class, () -> requireSyntheticLegacy(c));
            assertEquals(0, count(c,
                    "SELECT count(*) FROM pg_tables WHERE schemaname='public' " +
                    "AND tablename='" + HISTORY + "'"));
        }
    }
}
