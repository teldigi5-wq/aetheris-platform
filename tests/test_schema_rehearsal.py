"""Guard the deliberately non-deployed PostgreSQL schema rehearsal.

These assertions are static checks, not Hibernate schema validation and not a
substitute for comparing migrations with an actual persisted operator database.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
REHEARSAL = ROOT / "docs" / "database" / "rehearsal"


class SchemaRehearsalSafetyTests(unittest.TestCase):
    def test_sql_is_rehearsal_only_and_never_in_service_migration_paths(self):
        for file in ("V1__identity_schema.sql", "V1__users_schema.sql"):
            path = REHEARSAL / file
            self.assertTrue(path.is_file(), path)
            data = path.read_text(encoding="utf-8")
            self.assertIn("SYNTHETIC FRESH DATABASE REHEARSAL ONLY", data)
            self.assertNotRegex(
                data, r"(?im)^\\s*(?:DROP|TRUNCATE|DELETE|ALTER\\s+TABLE)\\b"
            )
        for service in ("identity-service", "user-service"):
            migration = ROOT / service / "src" / "main" / "resources" / "db" / "migration"
            self.assertFalse(migration.exists(), f"Unexpected auto-enabled migrations: {migration}")

    def test_identity_and_user_tables_follow_current_entities(self):
        identity_entity = (ROOT / "identity-service" / "src" / "main" /
                           "java/io/aetheris/identity/IdentityAccount.java").read_text()
        refresh_entity = (ROOT / "identity-service" / "src" / "main" /
                          "java/io/aetheris/identity/RefreshToken.java").read_text()
        user_entity = (ROOT / "user-service" / "src" / "main" /
                       "java/io/aetheris/users/User.java").read_text()
        identity_sql = (REHEARSAL / "V1__identity_schema.sql").read_text()
        user_sql = (REHEARSAL / "V1__users_schema.sql").read_text()
        for name in ("identity_accounts", "identity_refresh_tokens"):
            self.assertIn(f"CREATE TABLE {name}", identity_sql)
        self.assertIn("CREATE TABLE users", user_sql)
        self.assertIn('@Table(name = "identity_accounts"', identity_entity)
        self.assertIn('@Table(name = "identity_refresh_tokens"', refresh_entity)
        self.assertIn('@Table(name = "users")', user_entity)
        self.assertIn("password_hash VARCHAR(100) NOT NULL", identity_sql)
        self.assertIn("token_hash VARCHAR(64) NOT NULL", identity_sql)
        self.assertIn("FOREIGN KEY (account_id)", identity_sql)
        self.assertIn("idx_refresh_token_hash", identity_sql)
        self.assertIn("email VARCHAR(255) NOT NULL", user_sql)

    def test_rehearsal_does_not_silently_claim_auto_ddl_issue_is_fixed(self):
        compose = ("docker-compose.yml", "docker-compose.core.yml",
                   "docker-compose.integration-external.yml")
        observed = {
            name: (ROOT / name).read_text().count("SPRING_JPA_HIBERNATE_DDL_AUTO: update")
            for name in compose
        }
        # This test intentionally records the still-unresolved risk in the
        # rehearsal baseline. Change this assertion as part of a separate,
        # validated migration cutover, never as a cosmetic patch.
        for name, count in observed.items():
            self.assertEqual(count, 2, f"Reassess migration status for {name}: {count}")
        doc = (ROOT / "docs/database/SCHEMA_REHEARSAL.md").read_text()
        self.assertIn("UNRESOLVED", doc)
        self.assertIn("Flyway", doc)


if __name__ == "__main__":
    unittest.main()
