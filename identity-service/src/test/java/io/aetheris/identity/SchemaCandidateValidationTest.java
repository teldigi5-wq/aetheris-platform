package io.aetheris.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

/**
 * Isolated schema rehearsal. No test profile may auto-create tables.
 * Enabled only when the CI rehearsal runs -Dtest=SchemaCandidateValidationTest.
 */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaCandidateValidationTest {
    @Test
    void ormEntityModelMatchesPreparedPostgresSchema() {
        // A successful Spring/JPA application context start executes Hibernate validate.
    }
}
