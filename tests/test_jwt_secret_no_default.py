"""JWT signing-secret boundary: no repository default, fail closed everywhere.

The former default `change-me-local-development-secret-32chars` was published in this
public repository; anyone holding it can forge tokens for any role or scope.
"""

from pathlib import Path
import hashlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FORMER_DEFAULT = "change-me-local-development-secret-32chars"
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.core.yml", "docker-compose.integration-external.yml")
POLICIES = (
    "gateway/src/main/java/io/aetheris/gateway/JwtSecretPolicy.java",
    "identity-service/src/main/java/io/aetheris/identity/JwtSecretPolicy.java",
)


class JwtSecretNoDefaultTest(unittest.TestCase):
    def test_service_configs_have_no_fallback_secret(self) -> None:
        for path in ("gateway/src/main/resources/application.yml", "identity-service/src/main/resources/application.yml"):
            text = (ROOT / path).read_text(encoding="utf-8")
            self.assertIn("secret: ${AETHERIS_JWT_SECRET:}", text, path)
            self.assertNotIn(FORMER_DEFAULT, text, path)

    def test_compose_requires_secret_and_has_no_default(self) -> None:
        for name in COMPOSE_FILES:
            text = (ROOT / name).read_text(encoding="utf-8")
            self.assertNotIn(FORMER_DEFAULT, text, name)
            refs = re.findall(r"AETHERIS_JWT_SECRET: (\S.*)", text)
            self.assertEqual(2, len(refs), f"{name}: expected gateway + identity")
            for ref in refs:
                self.assertTrue(ref.startswith("${AETHERIS_JWT_SECRET:?"), f"{name}: {ref}")

    def test_both_services_construct_keys_through_the_policy(self) -> None:
        for path in ("gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java",
                     "identity-service/src/main/java/io/aetheris/identity/JwtService.java"):
            self.assertIn("JwtSecretPolicy.requireStrong(secret)", (ROOT / path).read_text(encoding="utf-8"), path)

    def test_policies_are_identical_and_block_the_former_default(self) -> None:
        bodies = [re.sub(r"^package .*;$", "", (ROOT / p).read_text(encoding="utf-8"), flags=re.M) for p in POLICIES]
        self.assertEqual(bodies[0], bodies[1], "gateway and identity policies drifted")
        self.assertIn(hashlib.sha256(FORMER_DEFAULT.encode()).hexdigest(), bodies[0])

    def test_no_signing_secret_literal_reintroduced_in_java(self) -> None:
        hits = [str(p.relative_to(ROOT)) for p in ROOT.glob("*/src/**/*.java")
                if FORMER_DEFAULT in p.read_text(encoding="utf-8") and "/test/" not in str(p)]
        self.assertEqual([], hits)

    def test_every_workflow_that_starts_compose_supplies_a_secret(self) -> None:
        missing = []
        for wf in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
            text = wf.read_text(encoding="utf-8")
            if re.search(r"docker[ -]compose", text) and "AETHERIS_JWT_SECRET" not in text:
                missing.append(wf.name)
        self.assertEqual([], missing)

    def test_env_example_has_no_value(self) -> None:
        text = (ROOT / ".env.example").read_text(encoding="utf-8")
        self.assertRegex(text, r"(?m)^AETHERIS_JWT_SECRET=$")


if __name__ == "__main__":
    unittest.main()
