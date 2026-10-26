"""B4Y offline regression: preserve test-only Audit recovery and private auth."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests/security"))
from probe_audit_restored import health_is_up, read
OVERLAY = ROOT / "tests/security/docker-compose.audit-restore-overlay.yml"
WORKFLOW = ROOT / ".github/workflows/audit-disposable-restore-replay.yml"
AUDIT = ROOT / "audit-service/src/main/resources/application.yml"


class AuditRestoreBoundaryTests(unittest.TestCase):
    def test_real_positive_health(self):
        self.assertTrue(health_is_up(b'{"status":"UP"}'))

    def test_degraded_health_is_not_accepted(self):
        for payload in (b'{"status":"DOWN"}', b'{"status":"UNKNOWN"}',
                        b'{"status":"UP","garbage":', b'null', b'[]', b'{}',
                        b'{"status":true}', b'{"status":"up"}'):
            with self.subTest(payload=payload):
                self.assertFalse(health_is_up(payload))

    def test_probe_cannot_request_arbitrary_routes(self):
        for path in ("/api/auth", "https://example.com", "/actuator/env",
                     "/api/events/1", "/api/events?limit=1"):
            with self.subTest(path=path):
                with self.assertRaises(ValueError):
                    read(path)

    def test_overlay_contains_no_automatic_baseline_or_host_exposure(self):
        text = OVERLAY.read_text()
        self.assertNotIn("SPRING_FLYWAY_BASELINE_ON_MIGRATE", text)
        self.assertNotIn("SPRING_FLYWAY_TABLE:", text)
        self.assertNotIn("ports:", text)
        self.assertNotIn("/var/run/docker.sock", text)
        self.assertNotIn("privileged:", text)
        self.assertNotIn("ddl-auto: update", text)

    def test_restore_instances_use_disposable_databases(self):
        text = OVERLAY.read_text()
        for label in ("audit-restored:", "audit-restore-corrupted:",
                      "audit-restore-probe:"):
            self.assertIn(label, text)
        for db in ("aetheris_audit_restored", "aetheris_audit_corrupted"):
            self.assertIn("jdbc:postgresql://postgres:5432/" + db, text)
        self.assertIn("cap_drop: [\"ALL\"]", text)
        self.assertIn("read_only: true", text)

    def test_flyway_shipped_default_still_fails_closed(self):
        text = AUDIT.read_text()
        self.assertIn("baseline-on-migrate: false", text)
        self.assertNotIn("baseline-on-migrate: true", text)

    def test_workflow_never_touches_operational_compose(self):
        text = WORKFLOW.read_text()
        for forbidden in ("docker-compose.yml up", "docker-compose.yml down",
                          "docker compose down -v", "pg_restore --clean",
                          "flyway baseline", "flyway repair"):
            self.assertNotIn(forbidden, text)
        self.assertIn("pg_dump", text)
        self.assertIn("pg_restore", text)
        self.assertIn("B4Y_RECOVERY", text)


if __name__ == "__main__":
    unittest.main()
