package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SINGLE synthetic physical PostgreSQL database, one Flyway history per
 * service-specific schema. Neither migration runs on the public schema.
 * CI opt-in only; not a production historical-schema migration.
 */
@EnabledIfSystemProperty(named = "aetheris.flyway.shared.synthetic", matches = "true")
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=false",
    "spring.flyway.clean-disabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SharedDatabaseSchemaIsolationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayAndHibernateValidateOnlyOurLogicalSchema() {
        assertEquals("identity_stage",
            jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM identity_stage.flyway_schema_history " +
            "WHERE version='1' AND success", Integer.class));
        assertEquals(1, jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema='identity_stage' AND table_name='identity_accounts'", Integer.class));
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema='public' AND table_name IN " +
            "('identity_accounts','identity_refresh_tokens','users','flyway_schema_history')",
            Integer.class));
        // Namespace isolation is a structural assertion, NOT an authentication
        // or database-role least-privilege proof: tests use a privileged CI DB role.
        assertEquals(0, jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
            "WHERE table_schema='user_stage' AND table_name='identity_accounts'", Integer.class));
    }
}
