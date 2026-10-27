package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dedicated disposable PostgreSQL ONLY. The normal app disables Flyway.
 * Hibernate validates the schema after the deliberately opted-in migration.
 * NEVER enable this against unknown existing customer histories.
 */
@EnabledIfSystemProperty(named = "aetheris.flyway.synthetic", matches = "true")
@DataJpaTest(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.baseline-on-migrate=false",
    "spring.flyway.clean-disabled=true",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayOptInFreshDatabaseTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void realVersionedFlywayHistoryAndHibernateSchemaMustMatch() {
        Integer applied = jdbc.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success",
            Integer.class);
        assertEquals(1, applied);
        Integer exists = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema='public' AND table_name='identity_accounts'",
            Integer.class);
        assertEquals(1, exists);
    }
}
