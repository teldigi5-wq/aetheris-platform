import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("jwt_mode", ROOT / "scripts/security/check_rs256_compose_contract.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ComposeJwtModeContractTest(unittest.TestCase):
    def sample(self):
        return {
            "AETHERIS_JWT_SECRET": "",
            "AETHERIS_IDENTITY_JWT_ISSUER": "https://identity.example.invalid",
            "AETHERIS_BACKEND_JWT_ISSUER": "https://identity.example.invalid",
            "AETHERIS_IDENTITY_JWT_AUDIENCE": "user",
            "AETHERIS_USER_JWT_AUDIENCE": "user",
            "AETHERIS_AUDIT_JWT_AUDIENCE": "audit",
            "AETHERIS_ORCHESTRATOR_JWT_AUDIENCE": "orch",
            "AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM": "-----BEGIN PRIVATE KEY-----review-only-----END PRIVATE KEY-----",
            "AETHERIS_JWT_RS256_PUBLIC_KEY_PEM": "-----BEGIN PUBLIC KEY-----review-only-----END PUBLIC KEY-----",
            "AETHERIS_IDENTITY_JWT_RS256_KEY_ID": "release-2026-1",
            "AETHERIS_JWT_RS256_KEY_ID": "release-2026-1",
        }

    def test_mode_matrix_deny_unprovisioned_legacy_and_accept_valid_synthetic_contract(self):
        self.assertTrue(module.validate("legacy", {}))
        self.assertEqual([], module.validate("legacy", {"AETHERIS_JWT_SECRET": "x"*40}))
        self.assertEqual([], module.validate("rs256", self.sample()))
        self.assertTrue(module.validate("rs256", {}))

    def test_fails_closed_for_partial_mixed_and_confused_deputy_config(self):
        base = self.sample()
        for key, value in (
            ("AETHERIS_JWT_SECRET", "shared-legacy"),
            ("AETHERIS_BACKEND_JWT_ISSUER", "https://wrong.invalid"),
            ("AETHERIS_USER_JWT_AUDIENCE", "audit"),
            ("AETHERIS_IDENTITY_JWT_AUDIENCE", "other"),
            ("AETHERIS_JWT_RS256_KEY_ID", "different"),
            ("AETHERIS_IDENTITY_JWT_RS256_KEY_ID", " space "),
            ("AETHERIS_JWT_RS256_STAGED_KEY_ID", "staged-only"),
            ("AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM", "unpaired"),
            ("AETHERIS_JWT_RS256_PUBLIC_KEY_PEM", ""),
            ("AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM", ""),
        ):
            with self.subTest(key=key, value=value):
                settings = dict(base); settings[key] = value
                self.assertTrue(module.validate("rs256", settings))

    def test_compose_three_variants_have_service_specific_policy_and_no_inline_private_key(self):
        for filename in ("docker-compose.yml", "docker-compose.core.yml",
                         "docker-compose.integration-external.yml"):
            with self.subTest(filename=filename):
                value=(ROOT / filename).read_text()
                self.assertEqual(4, value.count("AETHERIS_JWT_SECRET: ${AETHERIS_JWT_SECRET:-}"))
                self.assertEqual(1, value.count("AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM:"))
                self.assertEqual(3, value.count("JWT_RS256_PUBLIC_KEY_PEM:"))
                for key in ("AETHERIS_USER_JWT_AUDIENCE",
                            "AETHERIS_AUDIT_JWT_AUDIENCE",
                            "AETHERIS_ORCHESTRATOR_JWT_AUDIENCE"):
                    self.assertIn("${"+key+":-}", value)
                self.assertNotIn("-----BEGIN PRIVATE KEY-----", value)
                self.assertNotIn("${AETHERIS_JWT_SECRET:?", value)

if __name__=="__main__":
    unittest.main()
