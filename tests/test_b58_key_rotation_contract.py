"""B58 scope locks: synthetic rotation only; genuine HTTP workload denial."""
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


class B58Guardrails(unittest.TestCase):
    def test_b58_rotation_crypto_is_test_only(self):
        source=(ROOT/"identity-service/src/test/java/io/aetheris/identity/JwtRotationB58Test.java").read_text()
        self.assertIn("SYNTHETIC REHEARSAL ONLY",source)
        self.assertIn("parseSignedClaims(jwt)",source)
        self.assertIn("retiredOldKeyImmediatelyDeniedAfterOverlapWindow",source)
        self.assertIn("boundedOverlapAcceptsOnlyBothExplicitlyInstalledEpochs",source)
        self.assertIn("legacyKidlessEvenWithLegitimateKeyIsNeverAutomaticallyAccepted",source)

    def test_rotation_rehearsal_disallows_cross_service_audience_confusion(self):
        source=(ROOT/"identity-service/src/test/java/io/aetheris/identity/JwtRotationB58Test.java").read_text()
        self.assertIn("workloadTokenMustNotBecomeAPlatformApiCredential",source)
        self.assertIn("ambiguousMultiAudienceTokenIsDeniedEvenWhenApiAudiencePresent",source)
        self.assertIn("MAX_LIFETIME_SECONDS = 300",source)

    def test_real_gateway_and_both_backend_verifiers_reject_multiple_audiences(self):
        for path in [
            "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
            "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
            "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
        ]:
            with self.subTest(path=path):
                src=(ROOT/path).read_text()
                self.assertIn("Set.of(audience).equals(claims.getAudience())",src)
                self.assertIn(".requireIssuer(issuer)",src)
                self.assertIn(".requireAudience(audience)",src)
                self.assertIn(".verifyWith(key)",src)

    def test_b58_new_identity_fixtures_are_real_signed_test_only_tokens(self):
        src=(ROOT/"identity-service/src/test/java/io/aetheris/identity/IdentityIssuedJwtContractIT.java").read_text()
        self.assertIn('"workload", workload',src)
        self.assertIn('"foreign", foreign',src)
        self.assertIn('"dual", dual',src)
        self.assertIn("new JwtService(TEST_SECRET, 300",src)
        self.assertIn("Keys.hmacShaKeyFor(TEST_SECRET",src)

    def test_probe_uses_exact_four_internal_targets_and_no_external_network(self):
        src=(ROOT/"tests/security/probe_b58_workload_audience.py").read_text()
        for expected in ("http://gateway:8080","http://user-service:8081","http://audit-service:8083",
                         '"workload": 401','"foreign": 401','"dual": 401','"admin": 200',
                         "count != 16","no"):
            if expected=="no":
                self.assertNotIn("subprocess",src)
            else:
                self.assertIn(expected,src)

    def test_probe_is_still_unprivileged_on_internal_only_network(self):
        src=(ROOT/"tests/security/docker-compose.backend-auth-probe.yml").read_text()
        self.assertIn("./probe_b58_workload_audience.py:/opt/workload-probe.py:ro",src)
        self.assertIn("    internal: true",src)
        self.assertIn("    read_only: true",src)
        self.assertIn('cap_drop: ["ALL"]',src)
        self.assertNotIn("docker.sock",src)

    def test_production_jwt_has_not_silently_enabled_key_rotation(self):
        src=(ROOT/"identity-service/src/main/java/io/aetheris/identity/JwtService.java").read_text()
        self.assertIn("private final SecretKey key;",src)
        self.assertNotIn("kidResolver",src)
        self.assertNotIn("jwt.secret.previous",src)
        self.assertIn(".issuer(issuer)",src)
        self.assertIn(".audience().add(audience).and()",src)

    def test_admin_only_and_verified_scope_rules_remain(self):
        gw=(ROOT/"gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java").read_text()
        self.assertIn('if (!"ADMIN".equals(role))',gw)
        for svc,p in [
            ("user","user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java"),
            ("audit","audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java")]:
            with self.subTest(service=svc):
                src=(ROOT/p).read_text()
                self.assertIn('Objects.equals(role, "ADMIN")',src)
                self.assertIn("response.sendError(HttpServletResponse.SC_FORBIDDEN",src)


if __name__=="__main__":
    unittest.main()
