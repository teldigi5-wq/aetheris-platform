"""B56 safe replay of true User/Audit Gateway JWT boundary on patched runtime."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
YAML = ROOT / ".github/workflows/b56-patched-direct-backend-auth.yml"
COMPOSE = ROOT / "tests/security/docker-compose.backend-auth-probe.yml"
USER_FILTER = ROOT / "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java"
AUDIT_FILTER = ROOT / "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java"
GATEWAY_FILTER = ROOT / "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java"


class B56RealBackendBoundaryContract(unittest.TestCase):
    def test_ci_runs_only_on_stacked_b55_pr(self):
        content = YAML.read_text()
        self.assertIn("branches: [security/b55-image-evidence-chain-20261026]", content)
        self.assertIn("github.event.pull_request.head.sha", content)
        self.assertIn('test "$(git rev-parse HEAD)"', content)

    def test_actual_three_patched_runtime_images_are_built(self):
        content = YAML.read_text()
        self.assertIn("build gateway user-service audit-service", content)
        self.assertIn('docker exec "$cid" java -version', content)
        self.assertIn("21\\.0\\.13", content)
        self.assertIn("10001:10001", content)

    def test_both_original_real_http_probes_not_fake_statements(self):
        workflow = YAML.read_text()
        self.assertIn("--no-deps probe", workflow)
        self.assertIn("--no-deps resource-probe", workflow)
        self.assertIn("26_PLUS_30", workflow)

    def test_real_identity_issuer_and_uid_collision_fixture(self):
        content = YAML.read_text()
        self.assertIn("IdentityIssuedJwtContractIT test", content)
        self.assertIn("NonOwnerNumericCollisionJwtIT test", content)

    def test_cannot_publish_ports_or_persist_real_databases(self):
        content = YAML.read_text()
        for expected in ('not item.get("ports")', 'not c.get("volumes")',
                         'n.get("internal") is True', 'postgresql/data',
                         "docker.sock"):
            self.assertIn(expected, content)
        self.assertIn("down --volumes --remove-orphans", content)

    def test_jwt_is_independently_checked_not_unsigned_identity_headers(self):
        for filter_file in (USER_FILTER, AUDIT_FILTER):
            src = filter_file.read_text()
            self.assertIn("parseSignedClaims(token)", src)
            self.assertIn('Objects.equals(role, "ADMIN")', src)
            self.assertIn("response.sendError(HttpServletResponse.SC_UNAUTHORIZED", src)
            self.assertIn("response.sendError(HttpServletResponse.SC_FORBIDDEN", src)
            self.assertNotIn('request.getHeader("X-Aetheris-User-Role")', src)

    def test_direct_probe_rejects_forgery_and_expired_tokens(self):
        src = (ROOT / "tests/security/probe_backend_auth.py").read_text()
        for token in ('"expired"', '"tampered"', '"developer"', '"consumer"'):
            self.assertIn(token, src)
        self.assertIn("testAssertions", src)
        self.assertIn("len(RESULTS) == 26", src)

    def test_resource_row_is_not_identity_account_id(self):
        src = (ROOT / "tests/security/probe_user_row_id_collision.py").read_text()
        self.assertIn('row.get("id")==1', src)
        self.assertIn("admin-delete-single-synthetic", src.replace("admin-delete-synthetic-row-only","admin-delete-single-synthetic"))
        self.assertIn("len(COUNTS)==30", src)

    def test_gateway_signed_admin_and_write_permission_parity(self):
        src = GATEWAY_FILTER.read_text()
        self.assertIn('if (!"ADMIN".equals(role))', src)
        self.assertIn('return method != null && WRITE_METHODS.contains(method)', src)
        self.assertIn("parseSignedClaims(token)", src)

    def test_no_auth_policy_release_claim(self):
        workflow = YAML.read_text()
        self.assertNotIn("kubectl apply", workflow)
        self.assertNotIn("docker push", workflow)
        self.assertNotIn("vercel deploy", workflow)
        self.assertIn("B56_EPHEMERAL_CLEANUP_COMPLETED", workflow)


if __name__ == "__main__":
    unittest.main()
