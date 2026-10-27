"""Review-only guard for synthetic shared-DB least-privilege evidence.

These checks do not replace actual PostgreSQL, Flyway, or Java execution.
"""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/security/rehearse_shared_db_three_roles.sh"
APP = ROOT / "audit-service/src/main/resources/application.yml"
AUDIT_TEST = ROOT / "audit-service/src/test/java/io/aetheris/audit/SharedDatabaseAuditSchemaIsolationTest.java"
WORKFLOW = ROOT / ".github/workflows/security-three-service-role-isolation.yml"


class ThreeServiceRoleBoundaryContract(unittest.TestCase):
    def test_shell_script_is_parseable(self):
        subprocess.run(["bash", "-n", str(SCRIPT)], check=True)

    def test_audit_default_is_fail_closed(self):
        app = APP.read_text()
        self.assertIn("enabled: ${AETHERIS_AUDIT_FLYWAY_MIGRATION_ENABLED:false}", app)
        self.assertIn("baseline-on-migrate: false", app)
        self.assertIn("clean-disabled: true", app)
        self.assertIn("validate-on-migrate: true", app)
        self.assertIn("table: flyway_audit_schema_history", app)

    def test_audit_test_uses_real_jdbc_flyway_without_baselining(self):
        code = AUDIT_TEST.read_text()
        for phrase in (
            "aetheris.audit.shared.synthetic",
            "@JdbcTest(",
            "AutoConfigureTestDatabase.Replace.NONE",
            "spring.flyway.enabled=true",
            "spring.flyway.baseline-on-migrate=false",
            "spring.flyway.clean-disabled=true",
            "audit_stage.flyway_audit_schema_history",
            "audit_stage' AND table_name='audit_events'",
        ):
            self.assertIn(phrase, code)

    def test_real_disposable_role_login_and_refusal_contract(self):
        script = SCRIPT.read_text()
        for phrase in (
            "AETHERIS_THREE_ROLE_SYNTHETIC_ONLY",
            "trap cleanup EXIT",
            "postgres:16-alpine",
            "-p 127.0.0.1:55436:5432",
            "CREATE ROLE identity_migrator",
            "CREATE ROLE user_migrator",
            "CREATE ROLE audit_migrator",
            "CREATE ROLE identity_runtime",
            "CREATE ROLE user_runtime",
            "CREATE ROLE audit_runtime",
            "CREATE SCHEMA identity_stage AUTHORIZATION identity_migrator",
            "CREATE SCHEMA user_stage AUTHORIZATION user_migrator",
            "CREATE SCHEMA audit_stage AUTHORIZATION audit_migrator",
            "REVOKE ALL ON DATABASE shared_synthetic FROM PUBLIC",
            "REVOKE ALL ON SCHEMA public FROM PUBLIC",
            "PGPASSWORD",
            "-h 127.0.0.1",
            "run_all_migrations",
            "assert_denied",
            "DELETE FROM identity_stage.flyway_schema_history",
            "audit_stage.flyway_audit_schema_history",
            "THREE_ROLE_SECOND_START_HISTORY_AND_SYNTHETIC_ROWS_PRESERVED",
            "pg_dump",
            "pg_restore",
            "THREE_ROLE_UNKNOWN_AUDIT_LEGACY_REFUSED_WITH_SENTINEL_PRESERVED",
            "THREE_ROLE_AUDIT_REAL_CONCURRENT_FIRST_START_ONE_MIGRATION_SUCCESS",
            "ConcurrentAuditFlywayMigrationTest",
            "THREE_ROLE_UNKNOWN_AUDIT_LEGACY_FAILED_FOR_UNRELATED_REASON",
            "STAGE45_NO_GO",
        ):
            self.assertIn(phrase, script)
        for forbidden in (
            "docker compose down -v",
            "flyway clean",
            "DROP DATABASE",
            "DROP SCHEMA",
        ):
            self.assertNotIn(forbidden, script)

    def test_real_concurrent_flyway_migrator_contract(self):
        source = (ROOT / "audit-service/src/test/java/io/aetheris/audit/ConcurrentAuditFlywayMigrationTest.java").read_text()
        for requirement in (
            'aetheris.audit.concurrent.synthetic',
            'jdbc:postgresql://127.0.0.1:55436/shared_synthetic?currentSchema=audit_race',
            'CountDownLatch',
            'Executors.newFixedThreadPool(2)',
            'start.countDown()',
            'migrationsExecuted',
            'assertEquals(1, executions',
            'flyway_audit_schema_history',
            'baselineOnMigrate(false)',
            'cleanDisabled(true)',
            'assertTimeoutPreemptively',
        ):
            self.assertIn(requirement, source)

    def test_ci_exact_head_nonproduction_and_gate(self):
        workflow = WORKFLOW.read_text()
        for phrase in (
            "persist-credentials: false",
            "github.event.pull_request.head.sha",
            "AETHERIS_THREE_ROLE_SYNTHETIC_ONLY=yes",
            "rehearse_shared_db_three_roles.sh",
            "test_three_service_roles.py",
            "security_closure_preflight.py --assert-blocked",
            "if: always()",
        ):
            self.assertIn(phrase, workflow)


if __name__ == "__main__":
    unittest.main()
