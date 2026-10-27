"""B62 closure: one physical database, independently namespaced Flyway histories."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=(ROOT/"scripts/security/rehearse_shared_db_scoped_flyway.sh").read_text()
WORKFLOW=(ROOT/".github/workflows/security-shared-db-scoped-flyway.yml").read_text()
DOC=(ROOT/"docs/security/SHARED_DATABASE_SCOPED_FLYWAY_20261027.md").read_text()

class SharedPhysicalDatabaseGuard(unittest.TestCase):
    def test_scoped_jpa_and_flyway_must_match_both_modules(self):
        for module,folder,namespace in (
            ("identity-service","identity","identity_stage"),
            ("user-service","users","user_stage"),
        ):
            f=ROOT/module/"src/test/java/io/aetheris"/folder/"SharedDatabaseSchemaIsolationTest.java"
            t=f.read_text()
            for phrase in (
                '@EnabledIfSystemProperty(named = "aetheris.flyway.shared.synthetic", matches = "true")',
                '"spring.flyway.enabled=true"',
                '"spring.flyway.baseline-on-migrate=false"',
                '"spring.flyway.clean-disabled=true"',
                '"spring.jpa.hibernate.ddl-auto=validate"',
                f'assertEquals("{namespace}"',
                f"FROM {namespace}.flyway_schema_history",
                "WHERE table_schema='public'",
            ):
                self.assertIn(phrase,t)
            c=(ROOT/module/"src/main/resources/application.yml").read_text()
            self.assertIn("enabled: ${AETHERIS_FLYWAY_MIGRATION_ENABLED:false}",c)

    def test_one_physical_db_private_schemas_and_restart(self):
        for phrase in (
            "AETHERIS_SHARED_SCHEMA_SYNTHETIC_ONLY",
            "-p 127.0.0.1:55435:5432",
            "postgres:16-alpine",
            "trap cleanup EXIT",
            "shared_synthetic",
            "CREATE SCHEMA identity_stage",
            "CREATE SCHEMA user_stage",
            "CREATE SCHEMA audit_stage",
            "SPRING_FLYWAY_SCHEMAS",
            "SPRING_FLYWAY_DEFAULT_SCHEMA",
            "?currentSchema=$namespace",
            "run_scoped identity-service identity_stage",
            "run_scoped user-service user_stage",
            "SHARED_SCHEMA_IDENTITY_AND_USER_FLYWAY_FIRST_START_SUCCESS",
            "SHARED_SCHEMA_RESTARTS_NO_DUPLICATE_MIGRATIONS_AND_ROWS_PRESERVED",
            "SHARED_SCHEMA_AUDIT_NOT_MIGRATED_NEEDS_INDEPENDENT_REVIEW",
            "SHARED_SCHEMA_TEST_ONLY_NO_PRODUCTION_ACTIVATION_NO_GO",
        ):
            self.assertIn(phrase,SCRIPT)
        for forbidden in ("DROP SCHEMA","DROP DATABASE","docker compose down -v",
                          "flyway clean","baselineOnMigrate=true"):
            self.assertNotIn(forbidden,SCRIPT)

    def test_ci_and_owner_review_limits(self):
        for phrase in (
            "github.event.pull_request.head.sha",
            "persist-credentials: false",
            "AETHERIS_SHARED_SCHEMA_SYNTHETIC_ONLY=yes",
            "mvnw -B -ntp -f pom.xml test",
            "if: always()",
            "scripts/security/rehearse_shared_db_scoped_flyway.sh",
        ):
            self.assertIn(phrase,WORKFLOW)
        for phrase in ("NO_GO","production","historical","owner",
                       "audit_stage","privileged","existing",
                       "separate","Flyway","PostgreSQL"):
            self.assertIn(phrase,DOC)

if __name__=="__main__":
    unittest.main()
