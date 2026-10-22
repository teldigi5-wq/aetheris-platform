"""Guard migration rehearsal candidates: never silently mutate an existing installation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
CANDIDATE = ROOT / "database" / "migration-candidates"
DDL_FILES = (
    CANDIDATE / "V1__identity_schema.sql",
    CANDIDATE / "V1__users_schema.sql",
)


class SchemaMigrationCandidateTests(unittest.TestCase):
    def test_candidate_is_not_wired_into_running_services(self):
        for svc in ("identity-service", "user-service"):
            application = (ROOT / svc / "src/main/resources/application.yml").read_text()
            self.assertIn("ddl-auto: validate", application)
            pom = (ROOT / svc / "pom.xml").read_text()
            self.assertNotIn("<artifactId>flyway-core</artifactId>", pom)
            self.assertFalse((ROOT / svc / "src/main/resources/db/migration").exists())
        for name in ("docker-compose.yml", "docker-compose.core.yml",
                     "docker-compose.integration-external.yml"):
            # Explicit known risk #242. Do not silently change runtime behavior
            # before old schema baselines and backup/restore gates are proven.
            text = (ROOT / name).read_text()
            self.assertIn("SPRING_JPA_HIBERNATE_DDL_AUTO: update", text)

    def test_versioned_candidates_are_additive_only_and_fail_on_old_tables(self):
        for path in DDL_FILES:
            with self.subTest(path=str(path)):
                sql = path.read_text(encoding="utf-8")
                self.assertIn("CREATE TABLE", sql)
                self.assertNotIn("IF NOT EXISTS", sql.upper().split("CREATE TABLE", 1)[1])
                self.assertIsNone(re.search(r"\\b(DROP|TRUNCATE|DELETE|ALTER|UPDATE)\\b", sql.upper()))
                self.assertNotIn("CASCADE", sql.upper())

    def test_candidate_tables_match_entity_name_contract(self):
        identity = DDL_FILES[0].read_text()
        users = DDL_FILES[1].read_text()
        self.assertIn("CREATE TABLE identity_accounts", identity)
        self.assertIn("CREATE TABLE identity_refresh_tokens", identity)
        self.assertIn("REFERENCES identity_accounts(id)", identity)
        self.assertIn("CREATE TABLE users", users)
        self.assertIn("CONSTRAINT uk_users_email UNIQUE (email)", users)
        for entity, table in (
            ("identity-service/src/main/java/io/aetheris/identity/IdentityAccount.java", "identity_accounts"),
            ("identity-service/src/main/java/io/aetheris/identity/RefreshToken.java", "identity_refresh_tokens"),
            ("user-service/src/main/java/io/aetheris/users/User.java", "users"),
        ):
            self.assertIn(table, (ROOT / entity).read_text())


if __name__ == "__main__":
    unittest.main()
