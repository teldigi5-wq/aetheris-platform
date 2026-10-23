"""Fail-closed review tests for the synthetic upgrade experiment only."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "docs/database/rehearsal"


class SyntheticUpgradeTests(unittest.TestCase):
    def test_candidate_is_only_in_docs_and_requires_synthetic_marker(self):
        sql = (BASE / "V2__synthetic_index_upgrade.sql").read_text()
        legacy = (BASE / "legacy_existing_synthetic.sql").read_text()
        self.assertIn("to_regclass('public.aetheris_schema_rehearsal_guard')", sql)
        self.assertIn("CI_ONLY_SYNTHETIC", sql)
        self.assertIn("CI_ONLY_SYNTHETIC", legacy)
        self.assertIn("CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_hash", sql)
        self.assertRegex(sql, r"(?i)RAISE EXCEPTION.*schema")
        self.assertNotRegex(sql, r"(?im)^\s*(?:DROP|TRUNCATE|DELETE|UPDATE|ALTER\s+TABLE)\b")
        self.assertFalse((ROOT / "identity-service/src/main/resources/db/migration").exists())
        self.assertFalse((ROOT / "user-service/src/main/resources/db/migration").exists())

    def test_migration_does_not_look_like_prod_readiness(self):
        doc = (ROOT / "docs/database/SCHEMA_REHEARSAL.md").read_text()
        self.assertIn("UNRESOLVED", doc)
        self.assertIn("not a production Flyway migration", doc)
        workflow = (ROOT / ".github/workflows/schema-existing-upgrade.yml").read_text()
        self.assertIn("--network none", workflow)
        self.assertIn("--single-transaction", workflow)
        self.assertIn("pg_restore", workflow)
        self.assertNotIn("docker compose down -v", workflow)


if __name__ == "__main__":
    unittest.main()
