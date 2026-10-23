"""Protect the separate Flyway lab from accidental startup/deployment wiring."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
LAB = ROOT / "docs/database/flyway-lab"


class FlywayLabIsolationTests(unittest.TestCase):
    def test_only_two_ci_migrations_and_no_classpath_install(self):
        expected = {"V1__ci_only_identity_user_tables.sql",
                    "V2__ci_only_token_index.sql"}
        self.assertEqual({p.name for p in LAB.glob("*.sql")}, expected)
        for service in ("identity-service", "user-service", "audit-service"):
            self.assertFalse((ROOT / service / "src/main/resources/db/migration"
                              / "V2__ci_only_token_index.sql").exists())
        identity_sql = (LAB / "V1__ci_only_identity_user_tables.sql").read_text()
        index_sql = (LAB / "V2__ci_only_token_index.sql").read_text()
        self.assertIn("CI_ONLY_SYNTHETIC", identity_sql)
        self.assertIn("CI_ONLY_SYNTHETIC", index_sql)
        self.assertIn("aetheris_schema_rehearsal_guard", index_sql)

    def test_java_harness_never_accepts_remote_jdbc_or_auto_baseline(self):
        source = (ROOT / "audit-service/src/test/java/io/aetheris/audit/"
                  "FlywaySchemaLabTest.java").read_text()
        self.assertIn("jdbc:postgresql://127.0.0.1:15435/", source)
        self.assertIn("baselineOnMigrate(false)", source)
        self.assertIn("cleanDisabled(true)", source)
        self.assertIn("requireSyntheticLegacy", source)
        self.assertIn("assertThrows", source)
        self.assertNotIn("Flyway.clean()", source)
        action = (ROOT / ".github/workflows/schema-flyway-lab.yml").read_text()
        self.assertIn("-p 127.0.0.1:15435:5432", action)
        self.assertIn("AETHERIS_SCHEMA_LAB_MODE=CI_ONLY_SYNTHETIC", action)
        self.assertNotIn("docker compose down -v", action)


if __name__ == "__main__":
    unittest.main()
