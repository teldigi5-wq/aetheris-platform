package io.aetheris.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Disposable PostgreSQL only. Overrides Audit's legacy baseline-on-migrate
 * setting to false and proves an isolated V1 Flyway history using a dedicated
 * migration login. It does NOT enable migrations on a real existing database.
 */
@EnabledIfSystemProperty(named = "aetheris.audit.shared.synthetic", matches = "true")
@JdbcTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=false",
    "spring.flyway.clean-disabled=true",
    "spring.flyway.validate-on-migrate=true",
    "spring.flyway.table=flyway_audit_schema_history"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedDatabaseAuditSchemaIsolationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void auditMigrationAndQueriesStayInItsOwnSchema() {
        assertEquals("audit_stage",
            jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM audit_stage.flyway_audit_schema_history "
            + "WHERE version='1' AND success", Integer.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM audit_stage.flyway_audit_schema_history",
            Integer.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables "
            + "WHERE table_schema='audit_stage' AND table_name='audit_events'",
            Integer.class));
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables "
            + "WHERE table_schema='public' AND table_name='audit_events'",
            Integer.class));
    }
}
