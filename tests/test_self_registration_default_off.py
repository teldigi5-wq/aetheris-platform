"""Self-registration boundary: sign-up is off unless the operator opts in.

Self-registered accounts receive API_CONSUMER (users:read, services:read,
orchestrator:read), which can list every user and read audit events. The
repository default must therefore be "off"; only CI proofs and explicit local
demos enable it.
"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.core.yml", "docker-compose.integration-external.yml")
WORKFLOWS = ROOT / ".github" / "workflows"
REGISTER = re.compile(r"/api/auth/(?:browser/)?register")


class SelfRegistrationDefaultOffTest(unittest.TestCase):
    def test_service_default_is_off(self) -> None:
        text = (ROOT / "identity-service/src/main/resources/application.yml").read_text(encoding="utf-8")
        self.assertIn("enabled: ${AETHERIS_SELF_REGISTRATION_ENABLED:false}", text)
        java = (ROOT / "identity-service/src/main/java/io/aetheris/identity/IdentityService.java").read_text(encoding="utf-8")
        self.assertIn('@Value("${aetheris.self-registration.enabled:false}")', java)

    def test_flag_is_checked_before_any_persistence(self) -> None:
        java = (ROOT / "identity-service/src/main/java/io/aetheris/identity/IdentityService.java").read_text(encoding="utf-8")
        body = java[java.index("public AuthResponse register("):]
        self.assertLess(body.index("if (!selfRegistrationEnabled)"), body.index("repository."))

    def test_both_register_endpoints_map_refusal_to_403(self) -> None:
        for name in ("AuthController.java", "BrowserAuthController.java"):
            text = (ROOT / "identity-service/src/main/java/io/aetheris/identity" / name).read_text(encoding="utf-8")
            handler = text[text.index("RegistrationDisabledException.class"):][:400]
            self.assertIn("FORBIDDEN", handler, name)

    def test_compose_defaults_to_off(self) -> None:
        for name in COMPOSE_FILES:
            text = (ROOT / name).read_text(encoding="utf-8")
            self.assertEqual(
                1, text.count("AETHERIS_SELF_REGISTRATION_ENABLED: ${AETHERIS_SELF_REGISTRATION_ENABLED:-false}"), name)

    def test_every_workflow_running_a_registering_tool_opts_in(self) -> None:
        registering_tools = [p.name for p in (ROOT / "tools").glob("*.py") if REGISTER.search(p.read_text(encoding="utf-8"))]
        self.assertTrue(registering_tools)
        missing = []
        for wf in sorted(WORKFLOWS.glob("*.yml")):
            text = wf.read_text(encoding="utf-8")
            if any(tool in text for tool in registering_tools) and 'AETHERIS_SELF_REGISTRATION_ENABLED: "true"' not in text:
                missing.append(wf.name)
        self.assertEqual([], missing)

    def test_no_workflow_or_compose_enables_it_by_default_for_everyone(self) -> None:
        for name in COMPOSE_FILES:
            self.assertNotIn("AETHERIS_SELF_REGISTRATION_ENABLED:-true", (ROOT / name).read_text(encoding="utf-8"), name)


if __name__ == "__main__":
    unittest.main()
