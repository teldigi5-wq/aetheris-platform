from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CORE_JAVA_ROOTS = (
    ROOT / "gateway",
    ROOT / "identity-service",
    ROOT / "user-service",
    ROOT / "audit-service",
)
ORCHESTRATOR_ROOT = ROOT / "orchestrator-service"
CONTRACT_PATH = ROOT / "contracts" / "ai-runtime-boundary.v1.json"
GATEWAY_CONFIG = ROOT / "gateway" / "src" / "main" / "resources" / "application.yml"
COMPOSE_CONFIG = ROOT / "docker-compose.yml"
ROOT_POM = ROOT / "pom.xml"
WORKFLOWS_ROOT = ROOT / ".github/workflows"
CORE_WORKFLOWS = (
    WORKFLOWS_ROOT / "build.yml",
    WORKFLOWS_ROOT / "codeql.yml",
    WORKFLOWS_ROOT / "stage24-foundation-hardening.yml",
    WORKFLOWS_ROOT / "stage24-foundation-bootstrap.yml",
)
AI_RUNTIME_SOURCE_NAMES = (
    "orchestrator-service",
    "workstation-agent",
    "aetheris-quant",
    "aetheris-reasoning",
)


def java_sources(root: Path):
    yield from root.rglob("*.java")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class CoreAiRuntimeBoundaryTest(unittest.TestCase):
    def test_core_does_not_import_orchestrator_java(self) -> None:
        violations: list[str] = []
        for root in CORE_JAVA_ROOTS:
            for path in java_sources(root):
                if "io.aetheris.orchestrator" in read(path):
                    violations.append(str(path.relative_to(ROOT)))
        self.assertEqual([], violations)

    def test_orchestrator_does_not_import_core_service_java(self) -> None:
        forbidden = re.compile(r"\bio\.aetheris\.(?:gateway|identity|users|audit)\b")
        violations: list[str] = []
        for path in java_sources(ORCHESTRATOR_ROOT):
            if forbidden.search(read(path)):
                violations.append(str(path.relative_to(ROOT)))
        self.assertEqual([], violations)

    def test_root_maven_reactor_is_core_owned(self) -> None:
        pom = read(ROOT_POM)
        modules = re.findall(r"<module>([^<]+)</module>", pom)
        self.assertEqual(
            ["gateway", "user-service", "identity-service", "audit-service"],
            modules,
        )

    def test_core_ci_workflows_do_not_own_ai_runtime_source(self) -> None:
        violations: list[str] = []
        for workflow in CORE_WORKFLOWS:
            text = read(workflow)
            for source_name in AI_RUNTIME_SOURCE_NAMES:
                if source_name in text:
                    violations.append(f"{workflow.relative_to(ROOT)} -> {source_name}")
        self.assertEqual([], violations)

    def test_workflows_do_not_select_orchestrator_from_core_reactor(self) -> None:
        violations: list[str] = []
        for workflow in sorted(WORKFLOWS_ROOT.glob("*.yml")):
            if "-pl orchestrator-service" in read(workflow):
                violations.append(str(workflow.relative_to(ROOT)))
        self.assertEqual(
            [],
            violations,
            "AI-runtime workflows must build orchestrator through -f orchestrator-service/pom.xml, not the core reactor",
        )

    def test_ai_runtime_transitional_workflows_preserve_owned_checks(self) -> None:
        build = read(WORKFLOWS_ROOT / "ai-runtime-build.yml")
        security = read(WORKFLOWS_ROOT / "ai-runtime-codeql.yml")
        foundation = read(WORKFLOWS_ROOT / "ai-runtime-foundation-hardening.yml")
        bootstrap = read(WORKFLOWS_ROOT / "ai-runtime-foundation-bootstrap.yml")
        combined = "\n".join((build, security, foundation, bootstrap))
        for source_name in AI_RUNTIME_SOURCE_NAMES:
            self.assertIn(source_name, combined)
        self.assertIn("github/codeql-action/analyze", security)
        self.assertIn("pip_audit", foundation)
        self.assertIn("workstation-agent/packaging/build-package.ps1", build)
        self.assertIn("clean package", foundation)

    def test_versioned_gateway_contract_matches_configuration(self) -> None:
        contract = json.loads(read(CONTRACT_PATH))
        gateway = contract["gateway"]
        self.assertEqual(1, contract["schemaVersion"])
        self.assertEqual("/api/orchestrator/**", gateway["pathPattern"])
        self.assertEqual("AETHERIS_ORCHESTRATOR_URI", gateway["upstreamEnvironment"])
        self.assertEqual("http://orchestrator-service:8090", gateway["defaultUpstream"])
        self.assertEqual("/fallback/orchestrator", gateway["fallbackPath"])
        gateway_config = read(GATEWAY_CONFIG)
        self.assertIn("- id: orchestrator-service", gateway_config)
        self.assertIn("- Path=/api/orchestrator/**", gateway_config)
        self.assertIn(
            "uri: ${AETHERIS_ORCHESTRATOR_URI:http://orchestrator-service:8090}",
            gateway_config,
        )
        self.assertIn("fallbackUri: forward:/fallback/orchestrator", gateway_config)
        compose = read(COMPOSE_CONFIG)
        self.assertIn(
            "AETHERIS_ORCHESTRATOR_URI: ${AETHERIS_ORCHESTRATOR_URI:-http://orchestrator-service:8090}",
            compose,
        )

    def test_contract_declares_ownership_without_claiming_source_is_split(self) -> None:
        contract = json.loads(read(CONTRACT_PATH))
        self.assertEqual(
            ["gateway", "identity-service", "user-service", "audit-service"],
            contract["ownership"]["corePlatform"],
        )
        self.assertEqual(
            ["orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent"],
            contract["ownership"]["aiRuntime"],
        )
        self.assertTrue(contract["migration"]["localComposeDefaultPreserved"])
        self.assertTrue(contract["migration"]["externalRuntimeEndpointSupported"])
        self.assertTrue(
            contract["migration"]["sourceExtractionAllowedOnlyAfterArtifactContractCertification"]
        )


if __name__ == "__main__":
    unittest.main()
