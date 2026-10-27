"""Flyway activation is opt-in only, with real negative legacy admission."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCRIPT=(ROOT/"scripts/security/flyway_optin_postgres_rehearsal.sh").read_text()
WF=(ROOT/".github/workflows/security-optin-flyway-fresh-database.yml").read_text()
DOC=(ROOT/"docs/security/FLYWAY_OPTIN_FRESH_REHEARSAL_20261027.md").read_text()

class FlywayIndependentContract(unittest.TestCase):
    def test_default_production_disabled_and_non_destructive(self):
        for svc in ("identity-service","user-service"):
            conf=(ROOT/svc/"src/main/resources/application.yml").read_text()
            pom=(ROOT/svc/"pom.xml").read_text()
            for item in (
                "enabled: ${AETHERIS_FLYWAY_MIGRATION_ENABLED:false}",
                "baseline-on-migrate: false",
                "clean-disabled: true",
                "ddl-auto: validate",
            ):
                self.assertIn(item,conf)
            self.assertIn("flyway-core",pom)
            self.assertIn("flyway-database-postgresql",pom)
            migration=(ROOT/svc/"src/main/resources/db/migration/V1__fresh_schema.sql").read_text()
            self.assertIn("CREATE TABLE",migration)
            self.assertNotIn("DROP TABLE",migration.upper())
            self.assertNotIn("CREATE TABLE IF NOT EXISTS",migration.upper())
            cls=(ROOT/svc/"src/test/java/io/aetheris"/
                ("identity" if svc=="identity-service" else "users")/
                "FlywayOptInFreshDatabaseTest.java").read_text()
            for mark in (
                '@EnabledIfSystemProperty(named = "aetheris.flyway.synthetic", matches = "true")',
                '"spring.flyway.enabled=true"',
                '"spring.flyway.baseline-on-migrate=false"',
                '"spring.flyway.clean-disabled=true"',
                '"spring.jpa.hibernate.ddl-auto=validate"',
                "flyway_schema_history",
                "assertEquals(1, applied)",
            ):
                self.assertIn(mark,cls)

    def test_real_postgres_rehearsal_rejects_legacy(self):
        for mark in (
            'AETHERIS_FLYWAY_SYNTHETIC_CI_ONLY:-',
            "-p 127.0.0.1:55433:5432",
            "postgres:16-alpine",
            "trap cleanup EXIT",
            "docker rm -f",
            "flyway_identity",
            "flyway_user",
            "flyway_unknown_legacy",
            "run_maven identity-service flyway_identity",
            "run_maven user-service flyway_user",
            "FLYWAY_REAL_CLEAN_DATABASE_IDENTITY_AND_USER_MIGRATIONS_PASS",
            "FLYWAY_REPEATED_STARTUP_IDEMPOTENT",
            "baselineOnMigrate",
            "FLYWAY_REAL_UNKNOWN_LEGACY_SCHEMA_REJECTED_UNCHANGED",
            "to_regclass('public.flyway_schema_history') IS NULL",
            "FLYWAY_TEST_ONLY_OWNER_APPROVAL_STILL_REQUIRED_NO_GO",
        ):
            self.assertIn(mark,SCRIPT)
        self.assertNotIn("docker compose down -v",SCRIPT)
        self.assertNotIn("DROP DATABASE",SCRIPT.upper())
        self.assertNotIn("flyway migrate -baseline",SCRIPT)

    def test_review_documented_never_deployment_permission(self):
        for mark in ("github.event.pull_request.head.sha","persist-credentials: false",
                     "AETHERIS_FLYWAY_SYNTHETIC_CI_ONLY=yes",
                     "mvnw -B -ntp -f pom.xml test","if: always()"):
            self.assertIn(mark,WF)
        for term in ("NO_GO","existing database","separate",
                     "historical", "owner", "Flyway",
                     "disabled", "production", "not approved"):
            self.assertIn(term,DOC)

if __name__=="__main__":
    unittest.main()
