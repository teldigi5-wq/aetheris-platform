#!/usr/bin/env python3
"""Fail-closed static guard for shipped Audit Flyway defaults, no external input.

Run from repository root on Windows or Linux; no YAML parser, app secrets,
Docker socket, databases, network, installation settings or file writes.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "audit-service/src/main/resources/application.yml"
AUDIT_SQL = ROOT / "audit-service/src/main/resources/db/migration/V1__create_audit_events.sql"
OVERLAY = ROOT / "tests/security/docker-compose.audit-history-overlay.yml"

class AuditFlywayDefaultDenyTest(unittest.TestCase):
    def test_default_is_explicit_false_once(self):
        lines = AUDIT.read_text(encoding="utf-8").splitlines()
        values = [s.strip() for s in lines
                  if s.strip().startswith("baseline-on-migrate:")
                  and not s.lstrip().startswith("#")]
        self.assertEqual(values, ["baseline-on-migrate: false"])

    def test_history_table_has_stable_explicit_name(self):
        text = AUDIT.read_text(encoding="utf-8")
        lines = [s.strip() for s in text.splitlines()
                 if s.strip().startswith("table:") and not s.lstrip().startswith("#")]
        self.assertEqual(lines, ["table: flyway_audit_schema_history"])
        self.assertIn("    enabled: true", text)
        self.assertIn('    baseline-version: "0"', text)

    def test_runtime_v1_migration_still_exists(self):
        text = AUDIT_SQL.read_text(encoding="utf-8")
        self.assertIn("audit_events", text)
        self.assertIn("CREATE TABLE", text)
        self.assertIn("idx_audit_events_occurred_at", text)

    def test_no_unsafe_operational_compose_override(self):
        for file in ("docker-compose.yml", "docker-compose.core.yml",
                     "docker-compose.integration-external.yml"):
            with self.subTest(file=file):
                text = (ROOT / file).read_text(encoding="utf-8")
                self.assertNotIn("SPRING_FLYWAY_BASELINE_ON_MIGRATE", text)

    def test_disposable_docker_uses_shipped_default_not_an_override(self):
        text = OVERLAY.read_text(encoding="utf-8")
        self.assertEqual(text.count("    image: aetheris-b4t-audit-test-only:local"), 3)
        self.assertNotIn("SPRING_FLYWAY_BASELINE_ON_MIGRATE", text)
        self.assertNotIn("SPRING_FLYWAY_TABLE", text)
        self.assertNotIn("ports:", text)
        self.assertNotIn("/var/run/docker.sock", text)

if __name__ == "__main__":
    unittest.main(verbosity=2)
