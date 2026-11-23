"""B62-G2 structural guards complement mandatory online Postgres/JWT checks."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
B = ROOT / "tests/security"
A = (B / "b62g2_signed_decision.py").read_text()
P = (B / "b62g2-readonly-role.sql").read_text()
H = (B / "b62g2-consumer-proof.sh").read_text()
W = (ROOT / ".github/workflows/b62g2-readonly-consumer-proof.yml").read_text()

class B62G2Contract(unittest.TestCase):
    def test_verifier_is_test_only_and_denies_on_failure(self):
        for phrase in ('NOT imported by production', 'compare_digest', 'TEST_SIGNING_KEYS',
                       'ReadonlyPsql', 'DENY', 'subprocess.run', 'timeout=self.timeout_seconds',
                       'BEGIN READ ONLY', 'SET LOCAL ROLE b62g_verifier',
                       'len(jwt) > MAX_JWT_CHARS', 'response is True',
                       'return result.returncode == 0 and result.stdout.strip() == "t"'):
            self.assertIn(phrase, A)
        self.assertNotIn('shell=True', A)
        self.assertNotIn('https://', A)

    def test_db_role_is_not_a_writer_or_schema_owner(self):
        for phrase in ('CREATE ROLE b62g_verifier NOLOGIN', 'REVOKE ALL ON ALL FUNCTIONS',
                       'GRANT SELECT', 'GRANT EXECUTE ON FUNCTION',
                       'synthetic_post_signature_allowed(bigint,text,text,bigint)'):
            self.assertIn(phrase, P)
        self.assertNotIn('GRANT INSERT', P)
        self.assertNotIn('GRANT UPDATE', P)
        self.assertNotIn('GRANT SUPERUSER', P)

    def test_mandatory_real_db_fixtures_restart_denial_on_outage(self):
        for phrase in ('docker compose', 'down --volumes --remove-orphans',
                       'b62g2-readonly-role.sql', 'b62g2_live_acceptance.py',
                       'restarted', 'outage', 'trap cleanup EXIT',
                       'raise_principal_epoch(42, 12000)',
                       'SET ROLE b62g_verifier'):
            self.assertIn(phrase, H)
        self.assertIn('github.event.pull_request.head.sha', W)
        self.assertIn('persist-credentials: false', W)
        self.assertIn('if: always()', W)
        self.assertIn('bash tests/security/b62g2-consumer-proof.sh', W)

    def test_no_release_claim(self):
        doc = (ROOT / "docs/security/B62G2_READONLY_VERIFIER_BOUNDARY_20261026.md").read_text()
        for term in ('NOT INTEGRATED', 'NO_GO', 'PostgreSQL', 'signature',
                     'owner approval', 'stale replicas', 'production',
                     'historical migration'):
            self.assertIn(term, doc)

if __name__ == "__main__":
    unittest.main()
