"""Gateway deny-by-default boundary.

- Only /api/auth/** is public among routed paths; every other route needs a token
  and an explicit scope rule, otherwise it is refused.
- Ambiguous paths are rejected before any routing decision.
- Actuator exposure defaults to health,info; internals are opt-in.
"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
FILTER = ROOT / "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java"
GATEWAY_YML = ROOT / "gateway/src/main/resources/application.yml"
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.core.yml", "docker-compose.integration-external.yml")


class GatewayDenyByDefaultTest(unittest.TestCase):
    def test_public_routes_are_an_explicit_allowlist(self) -> None:
        java = FILTER.read_text(encoding="utf-8")
        self.assertIn('PUBLIC_ROUTE_PREFIXES = List.of("/api/auth/")', java)
        self.assertNotIn("isProtectedRoute", java)

    def test_routes_without_scope_rule_are_refused(self) -> None:
        java = FILTER.read_text(encoding="utf-8")
        self.assertIn("if (requiredScope == null) {", java)
        self.assertIn('"No access policy for route"', java)

    def test_ambiguous_paths_checked_on_raw_path_first(self) -> None:
        java = FILTER.read_text(encoding="utf-8")
        body = java[java.index("public Mono<Void> filter("):]
        self.assertLess(body.index("isAmbiguousPath(rawPath)"), body.index("isPublicRoute(path)"))
        self.assertIn("getRawPath()", body)

    def test_every_routed_prefix_has_a_scope_rule(self) -> None:
        java = FILTER.read_text(encoding="utf-8")
        yml = GATEWAY_YML.read_text(encoding="utf-8")
        prefixes = {p.removesuffix("/**") for p in re.findall(r"- Path=(\S+)", yml) for p in p.split(",")}
        uncovered = [p for p in prefixes
                     if not p.startswith("/api/auth") and f'under(path, "{p}")' not in java]
        self.assertEqual([], uncovered, "routed prefixes with no scope rule")

    def test_actuator_exposure_defaults_to_health_info(self) -> None:
        yml = GATEWAY_YML.read_text(encoding="utf-8")
        self.assertIn("include: ${AETHERIS_GATEWAY_ACTUATOR_EXPOSURE:health,info}", yml)
        for name in COMPOSE_FILES:
            text = (ROOT / name).read_text(encoding="utf-8")
            self.assertIn("AETHERIS_GATEWAY_ACTUATOR_EXPOSURE: ${AETHERIS_GATEWAY_ACTUATOR_EXPOSURE:-health,info}", text, name)


if __name__ == "__main__":
    unittest.main()
