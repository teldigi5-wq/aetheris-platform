"""B62-G source-only guards; real PostgreSQL is required by the workflow."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"tests/security"
SCHEMA=BASE/"b62g-revocation-prototype.sql"
PROOF=BASE/"b62g-revocation-proof.sh"
COMPOSE=BASE/"docker-compose.b62g-epoch-proof.yml"
WORKFLOW=ROOT/".github/workflows/b62g-revocation-postgres-prototype.yml"

class B62GPrototypePolicy(unittest.TestCase):
    def test_no_production_migration_or_auth_handler_is_changed(self):
        src=SCHEMA.read_text()
        self.assertIn("NOT an Identity/Flyway migration", src)
        for needle in ("CREATE SCHEMA b62g_proof", "principal_epochs",
                       "blocked_signer_kids", "ON CONFLICT (principal_id) DO UPDATE",
                       "GREATEST(", "p_signed_iat <=", "SECURITY INVOKER"):
            self.assertIn(needle, src)
        self.assertNotIn("DROP TABLE identity_", src)
        self.assertNotIn("DELETE FROM identity_", src)
        self.assertNotIn("SECURITY DEFINER", src)

    def test_isolated_database_can_only_be_created_as_ci_synthetic_volume(self):
        src=COMPOSE.read_text()
        self.assertIn("internal: true",src)
        self.assertIn("b62g_disposable_data",src)
        self.assertIn("POSTGRES_DB: b62g_synthetic",src)
        self.assertNotIn("ports:",src)
        self.assertNotIn("privileged: true",src)
        self.assertNotIn("docker.sock",src)

    def test_real_parallel_writers_restart_and_fail_closed_model_are_required(self):
        src=PROOF.read_text()
        for expected in ("for cutoff in 9100", "wait \"$pid\"", 
                         'test "$latest" = "12000"', "restart postgres",
                         "stop postgres", "case \"$result\" in",
                         '*) printf \'DENY\'', "trap cleanup EXIT",
                         "down --volumes --remove-orphans"):
            self.assertIn(expected,src)
        flow=WORKFLOW.read_text()
        self.assertIn("github.event.pull_request.head.sha",flow)
        self.assertIn("persist-credentials: false",flow)
        self.assertIn("bash tests/security/b62g-revocation-proof.sh",flow)
        self.assertIn("if: always()",flow)

    def test_explicit_not_integrated_disclaimer(self):
        runbook=(ROOT/"docs/security/B62G_REVOCATION_EPOCH_ARCHITECTURE_20261026.md").read_text()
        for needle in ("NOT integrated", "NO_GO", "migration", "emergency",
                       "stale replica", "timeout", "owner approval", "signature"):
            self.assertIn(needle,runbook)

if __name__=="__main__":
    unittest.main()
