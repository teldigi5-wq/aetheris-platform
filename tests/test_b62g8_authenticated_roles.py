"""B62-G8: exact-source isolated password login / revocation privilege guards."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
SCHEMA=(ROOT/"tests/security/b62g8-login-roles.sql").read_text()
PROOF=(ROOT/"tests/security/b62g8-password-roles-proof.sh").read_text()
WORKFLOW=(ROOT/".github/workflows/b62g8-authenticated-role-boundaries.yml").read_text()
RUNBOOK=(ROOT/"docs/security/B62G8_AUTHENTICATED_POSTGRES_ROLES_20261027.md").read_text()

class B62G8Contract(unittest.TestCase):
    def test_separate_authenticating_reader_identities_and_writer(self):
        for role in ("gateway_reader","user_reader","audit_reader","emergency_writer"):
            self.assertIn("CREATE ROLE b62g8_"+role+" LOGIN",SCHEMA)
        self.assertIn("GRANT b62g_verifier TO b62g8_gateway_reader",SCHEMA)
        self.assertIn("b62g8_user_reader, b62g8_audit_reader;",SCHEMA)
        self.assertIn("GRANT USAGE ON SCHEMA b62g_proof TO b62g8_emergency_writer;",SCHEMA)
        self.assertIn("GRANT SELECT, INSERT, UPDATE ON b62g_proof.principal_epochs",SCHEMA)
        self.assertIn("GRANT EXECUTE ON FUNCTION b62g_proof.raise_principal_epoch",SCHEMA)
        self.assertIn("REVOKE ALL ON ALL FUNCTIONS IN SCHEMA b62g_proof FROM PUBLIC;",SCHEMA)
        for unsafe in ("GRANT SUPERUSER", "GRANT DELETE", "ALTER ROLE", "SECURITY DEFINER",
                       "CREATE TABLE IF NOT EXISTS identity_"):
            self.assertNotIn(unsafe,SCHEMA)

    def test_real_password_login_and_cross_identity_checks(self):
        for s in ("-h 127.0.0.1 -p 5432 -U", "PGPASSWORD=", "b62g8_gateway_reader",
                  "b62g8_user_reader", "b62g8_audit_reader",
                  "b62g8_emergency_writer", "expect_forbidden",
                  "expect_signed_decision", "wait \"$pid\"", "restart postgres",
                  "stop postgres", "trap cleanup EXIT",
                  "down --volumes --remove-orphans",
                  "B62G8_THREE_DISTINCT_AUTHENTICATED_READERS_CANNOT_WRITE",
                  "B62G8_PRIMARY_READ_AFTER_COMMITTED_WRITER_UPDATE_PASS",
                  "B62G8_STOPPED_PRIMARY_FAILS_CLOSED_IN_TEST_CLIENT"):
            self.assertIn(s,PROOF)
        self.assertNotIn("SET LOCAL ROLE",PROOF)
        self.assertNotIn('-c "SET ROLE',PROOF)
        self.assertNotIn("ports:",PROOF)
        self.assertNotIn("docker.sock",SCHEMA)

    def test_exact_head_ci_still_runs_old_java_and_new_db_proofs(self):
        for s in ("github.event.pull_request.head.sha", "persist-credentials: false",
                  "mvnw -B -ntp -f pom.xml test",
                  "bash tests/security/b62g8-password-roles-proof.sh",
                  "if: always()"):
            self.assertIn(s,WORKFLOW)
        self.assertNotIn("deploy",WORKFLOW.lower())

    def test_missing_production_boundaries_explicit(self):
        for s in ("NOT INTEGRATED", "NO_GO", "production", "PostgreSQL",
                  "historical migration", "owner approval",
                  "read-after-write", "stale replicas",
                  "Gateway", "User", "Audit", "certificate",
                  "test-only", "superuser"):
            self.assertIn(s,RUNBOOK)

if __name__=="__main__":
    unittest.main()
