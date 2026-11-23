"""B62-G13 review-only privilege isolation and audit-contract source guards."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "tests/security"
SQL = (BASE/"b62g13-function-only-writer.sql").read_text()
SH = (BASE/"b62g13-function-only-writer-proof.sh").read_text()
WF = (ROOT/".github/workflows/b62g13-audited-emergency-privileges.yml").read_text()
DOC = (ROOT/"docs/security/B62G13_FUNCTION_ONLY_EMERGENCY_OWNER_REVIEW_20261027.md").read_text()


class B62G13Contract(unittest.TestCase):
    def test_separate_nonlogin_function_owner_and_restricted_writer(self):
        for statement in (
            "CREATE ROLE b62g13_exec_owner NOLOGIN NOSUPERUSER",
            "CREATE ROLE b62g13_emergency_writer LOGIN NOSUPERUSER",
            "CREATE ROLE b62g13_audit_reviewer LOGIN NOSUPERUSER",
            "SECURITY DEFINER",
            "SET search_path = pg_catalog, pg_temp",
            "ALTER FUNCTION b62g_proof.b62g13_raise_epoch",
            "OWNER TO b62g13_exec_owner;",
            "ALTER FUNCTION b62g_proof.b62g13_block_signer",
            "GRANT EXECUTE ON FUNCTION b62g_proof.b62g13_raise_epoch",
            "REVOKE ALL ON FUNCTION b62g_proof.b62g13_raise_epoch",
            "REVOKE ALL ON FUNCTION b62g_proof.b62g13_block_signer",
            "GRANT SELECT ON b62g_proof.b62g13_emergency_audit TO b62g13_audit_reviewer",
            "GRANT INSERT ON b62g_proof.b62g13_emergency_audit TO b62g13_exec_owner",
        ):
            self.assertIn(statement,SQL)
        self.assertNotIn("GRANT SUPERUSER",SQL)
        self.assertNotIn("GRANT DELETE",SQL)
        self.assertNotIn("GRANT INSERT, UPDATE ON b62g_proof.principal_epochs TO b62g13_emergency_writer",SQL)
        self.assertNotIn("GRANT UPDATE ON b62g_proof.b62g13_emergency_audit",SQL)
        self.assertNotIn("EXECUTE format(",SQL)
        self.assertNotIn("EXECUTE p_",SQL)

    def test_transactional_writer_side_logging_and_no_backwards_cutoff(self):
        for phrase in (
            "request_id text PRIMARY KEY",
            "session_user <> 'b62g13_emergency_writer'",
            "GREATEST(existing.reject_issued_at_or_before_epoch",
            "INSERT INTO b62g_proof.b62g13_emergency_audit",
            "INSERT INTO b62g_proof.principal_epochs AS existing",
            "INSERT INTO b62g_proof.blocked_signer_kids",
            "ON CONFLICT (issuer,kid) DO NOTHING;",
            "p_request_id !~ '^ci-[a-z0-9-]{6,56}$'",
        ):
            self.assertIn(phrase,SQL)
        self.assertNotIn("SECURITY INVOKER\nSET search_path",SQL)
        self.assertLess(SQL.index("INSERT INTO b62g_proof.b62g13_emergency_audit"),
                        SQL.index("INSERT INTO b62g_proof.principal_epochs AS existing"))

    def test_live_proof_negative_privileges_distinct_network_logins(self):
        for phrase in (
            "set -euo pipefail",
            'trap cleanup EXIT',
            'down --volumes --remove-orphans',
            'set(d["services"])=={"postgres","auth-probe"}',
            'no-new-privileges:true',
            'not svc.get("ports")',
            'auth-probe timeout 8s psql',
            '-h postgres -p 5432',
            "b62g13_emergency_writer invalid-test-password",
            "b62g13_audit_reviewer invalid-test-password",
            "B62G13_NETWORK_PASSWORDS_AND_READER_WRITE_ISOLATION_PASS",
            "B62G13_WRITER_FUNCTION_ONLY_TABLE_AND_AUDIT_DML_DENIED",
            "B62G13_FUNCTION_ONLY_WRITER_AUDIT_AND_REVOCATION_ATOMIC_PASS",
            "B62G13_REPLAY_AND_INVALID_REQUEST_ATOMIC_ROLLBACK_PASS",
            "'ci-reviewed-cutoff-001'",
            "'ci-reviewed-signer-003'",
            'for role in "${readers[@]}"; do',
            "expected=$2 actual=$1",
        ):
            self.assertIn(phrase,SH)
        self.assertNotIn("SET ROLE",SH)
        self.assertNotIn("docker.sock",SQL)
        self.assertNotIn("localhost",SH)

    def test_review_governance_and_ci_are_nonproduction(self):
        for phrase in (
            "github.event.pull_request.head.sha",
            "persist-credentials: false",
            "mvnw -B -ntp -f pom.xml test",
            "b62g13-function-only-writer-proof.sh",
            "b62g12_release_readiness.py",
            "if: always()",
        ):
            self.assertIn(phrase,WF)
        for phrase in (
            "NOT INTEGRATED", "NO_GO", "production", "owner approval",
            "historical migration", "read-after-write", "Gateway", "User",
            "Audit", "certificate", "function-only", "dual-control",
            "SECURITY DEFINER", "not tamper-proof",
        ):
            self.assertIn(phrase,DOC)


if __name__ == "__main__":
    unittest.main()
