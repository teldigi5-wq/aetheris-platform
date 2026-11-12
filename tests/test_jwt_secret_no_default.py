"""JWT signing-secret boundary: no repository default, fail closed everywhere (I7).

Ported from closed #423 onto the B62-F integration line, where four modules use the
HMAC secret: gateway, identity (signer), user and audit (direct verifiers).
The former default `change-me-local-development-secret-32chars` and the former gateway
test fixture were published in this public repository; anyone holding them can forge
tokens for any role or scope.
"""

from pathlib import Path
import hashlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FORMER_DEFAULT = "change-me-local-development-secret-32chars"
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.core.yml", "docker-compose.integration-external.yml")
MODULES = {
    "gateway": ("gateway/src/main/java/io/aetheris/gateway/JwtSecretPolicy.java",
                "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java"),
    "identity-service": ("identity-service/src/main/java/io/aetheris/identity/JwtSecretPolicy.java",
                         "identity-service/src/main/java/io/aetheris/identity/JwtService.java"),
    "user-service": ("user-service/src/main/java/io/aetheris/users/security/JwtSecretPolicy.java",
                     "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java"),
    "audit-service": ("audit-service/src/main/java/io/aetheris/audit/security/JwtSecretPolicy.java",
                      "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java"),
}


class JwtSecretNoDefaultTest(unittest.TestCase):
    def test_service_configs_have_no_fallback_secret(self) -> None:
        for module in MODULES:
            text = (ROOT / module / "src/main/resources/application.yml").read_text(encoding="utf-8")
            self.assertRegex(text, r"(?m)^    secret: \$\{AETHERIS_JWT_SECRET:?\}$", module)
            self.assertNotIn(FORMER_DEFAULT, text, module)

    def test_compose_requires_secret_for_every_jwt_module(self) -> None:
        for name in COMPOSE_FILES:
            text = (ROOT / name).read_text(encoding="utf-8")
            self.assertNotIn(FORMER_DEFAULT, text, name)
            refs = re.findall(r"AETHERIS_JWT_SECRET: (\S.*)", text)
            self.assertEqual(4, len(refs), f"{name}: expected gateway, identity, user, audit")
            for ref in refs:
                self.assertTrue(ref.startswith("${AETHERIS_JWT_SECRET:?"), f"{name}: {ref}")

    def test_every_hmac_key_is_constructed_through_the_policy(self) -> None:
        for module, (_, user) in MODULES.items():
            self.assertIn("JwtSecretPolicy.requireStrong(secret);", (ROOT / user).read_text(encoding="utf-8"), module)

    def test_policies_are_identical_and_block_published_values(self) -> None:
        bodies = [re.sub(r"(?m)^package .*;$", "", (ROOT / policy).read_text(encoding="utf-8"))
                  for policy, _ in MODULES.values()]
        self.assertEqual(1, len(set(bodies)), "JwtSecretPolicy copies drifted between modules")
        self.assertIn(hashlib.sha256(FORMER_DEFAULT.encode()).hexdigest(), bodies[0])
        self.assertIn(hashlib.sha256(("0123456789abcdef" * 2).encode()).hexdigest(), bodies[0])

    def test_no_published_secret_literal_in_java_sources_or_tests(self) -> None:
        fixture = "0123456789abcdef" * 2
        hits = [str(p.relative_to(ROOT)) for p in ROOT.glob("*/src/**/*.java")
                if (FORMER_DEFAULT in (t := p.read_text(encoding="utf-8")) and "/test/" not in str(p))
                or f'"{fixture}"' in t]
        self.assertEqual([], hits)

    def test_every_workflow_that_starts_compose_supplies_a_secret(self) -> None:
        missing = []
        for wf in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
            text = wf.read_text(encoding="utf-8")
            if not re.search(r"docker[ -]compose(?:\s+-f\s+\S+)*\s+up\b", text) or "AETHERIS_JWT_SECRET" in text:
                continue
            # Test-only probe stacks may carry their own synthetic secret in the
            # compose files the workflow names with -f.
            named = {m for m in re.findall(r"-f\s+(\S+\.ya?ml)", text) if (ROOT / m).is_file()}
            if not any("AETHERIS_JWT_SECRET" in (ROOT / m).read_text(encoding="utf-8") for m in named):
                missing.append(wf.name)
        self.assertEqual([], missing)

    def test_env_example_has_no_value(self) -> None:
        text = (ROOT / ".env.example").read_text(encoding="utf-8")
        self.assertRegex(text, r"(?m)^AETHERIS_JWT_SECRET=$")


if __name__ == "__main__":
    unittest.main()
