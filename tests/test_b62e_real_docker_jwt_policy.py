"""B62-E contracts for disposable end-to-end JWT HTTP and startup tests.

These source checks never replace live Docker evidence. Fail rather than
silently skipping an integration requirement or weakening the security harness.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "tests/security/docker-compose.b62e-real-jwt-overlay.yml"
PROBE = ROOT / "tests/security/probe_b62e_token_lifetime.py"
WORKFLOW = ROOT / ".github/workflows/b62e-real-jwt-http-and-issuer-startup.yml"

class B62EDisposableHTTPContract(unittest.TestCase):
    def test_only_internal_disposable_network_and_nonprivileged_probe(self):
        src = OVERLAY.read_text()
        self.assertIn("networks: [probe]", src)
        self.assertIn("read_only: true", src)
        self.assertIn('cap_drop: ["ALL"]', src)
        self.assertIn('security_opt: ["no-new-privileges:true"]', src)
        self.assertIn("identity-lifetime:", src)
        self.assertIn("ttl-probe:", src)
        self.assertNotIn("ports:", src)
        self.assertNotIn("docker.sock", src)
        self.assertNotIn("privileged: true", src)

    def test_real_http_probe_rejects_overlong_and_zero_duration(self):
        src = PROBE.read_text()
        for phrase in ('"current": 200', '"previous": 200',
                       '"max-lifetime": 200', '"overlong-lifetime": 401',
                       '"zero-lifetime": 401', "assert checks == 20"):
            self.assertIn(phrase, src)
        self.assertIn("url not in ROUTES.values() and url != IDENTITY_HEALTH", src)
        self.assertIn("B62E_UNVERIFIED_IDENTITY_SIGNING_FIXTURES", src)

    def test_exact_sha_full_regression_and_fail_closed_identity_startup(self):
        src = WORKFLOW.read_text()
        self.assertIn("github.event.pull_request.head.sha", src)
        self.assertIn("persist-credentials: false", src)
        self.assertIn("B62E_REAL_DISPOSABLE_RSA_HTTP", src)
        self.assertIn("AETHERIS_JWT_EXPIRATION_SECONDS", src)
        self.assertIn("JWT access-token lifetime must be 1..3600 seconds", src)
        self.assertIn("B61_AFTER_DEADLINE_PRIOR_SIGNER_DENIED_WITHOUT_JAVA_CONTAINER_RESTART", src)
        self.assertIn("Always remove only disposable CI containers network and tmpfs volumes", src)
        self.assertIn("docker compose", src)
        self.assertIn("identity-lifetime", src)

if __name__ == "__main__":
    unittest.main()
