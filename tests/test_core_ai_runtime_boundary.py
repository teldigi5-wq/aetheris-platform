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
CONTRACT_PATH = ROOT / "contracts" / "ai-runtime-boundary.v1.json"
CERTIFICATION_PATH = ROOT / "architecture" / "ai-runtime-certification-reference.json"
EXTRACTION_PATH = ROOT / "architecture" / "ai-runtime-extraction-manifest.json"
GATEWAY_CONFIG = ROOT / "gateway" / "src" / "main" / "resources" / "application.yml"
DEFAULT_COMPOSE = ROOT / "docker-compose.yml"
EXTERNAL_COMPOSE = ROOT / "docker-compose.integration-external.yml"
ROOT_POM = ROOT / "pom.xml"
WORKFLOWS_ROOT = ROOT / ".github" / "workflows"
AI_RUNTIME_SOURCE_NAMES = (
    "orchestrator-service",
    "workstation-agent",
    "aetheris-quant",
    "aetheris-reasoning",
)
EXPECTED_RUNTIME_SHA = "68af39a1115a7330020c18b6e2cb601e66b8f22f"


def java_sources(root: Path):
    yield from root.rglob("*.java")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


class CoreAiRuntimeBoundaryTest(unittest.TestCase):
    def test_core_does_not_import_runtime_java(self) -> None:
        forbidden = re.compile(r"\bio\.aetheris\.(?:orchestrator|workstation)\b")
        violations: list[str] = []
        for root in CORE_JAVA_ROOTS:
            for path in java_sources(root):
                if forbidden.search(read(path)):
                    violations.append(str(path.relative_to(ROOT)))
        self.assertEqual([], violations)

    def test_runtime_owned_source_is_physically_absent(self) -> None:
        present = [name for name in AI_RUNTIME_SOURCE_NAMES if (ROOT / name).exists()]
        self.assertEqual([], present)

    def test_root_maven_reactor_is_core_owned(self) -> None:
        pom = read(ROOT_POM)
        modules = re.findall(r"<module>([^<]+)</module>", pom)
        self.assertEqual(
            ["gateway", "user-service", "identity-service", "audit-service"],
            modules,
        )

    def test_runtime_owned_workflows_are_absent(self) -> None:
        manifest = json.loads(read(EXTRACTION_PATH))
        removed = manifest["runtime_owned_workflows_removed_from_platform"]
        self.assertEqual(33, len(removed))
        self.assertEqual(33, len(set(removed)))
        leaked = [relative for relative in removed if (ROOT / relative).exists()]
        self.assertEqual([], leaked)

    def test_workflows_do_not_select_or_build_local_runtime_source(self) -> None:
        violations: list[str] = []
        for workflow in sorted(WORKFLOWS_ROOT.glob("*.yml")):
            text = read(workflow)
            for token in (
                "-pl orchestrator-service",
                "build: ./orchestrator-service",
                "-f orchestrator-service/pom.xml",
            ):
                if token in text:
                    violations.append(f"{workflow.relative_to(ROOT)} -> {token}")
        self.assertEqual([], violations)

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

    def test_contract_declares_completed_external_ownership(self) -> None:
        contract = json.loads(read(CONTRACT_PATH))
        self.assertEqual(
            ["gateway", "identity-service", "user-service", "audit-service"],
            contract["ownership"]["corePlatform"],
        )
        self.assertEqual(list(AI_RUNTIME_SOURCE_NAMES[:1]) + ["aetheris-quant", "aetheris-reasoning", "workstation-agent"], contract["ownership"]["aiRuntime"])
        self.assertFalse(contract["sourceBoundary"]["platformContainsAiRuntimeSource"])
        self.assertFalse(contract["migration"]["localComposeDefaultPreserved"])
        self.assertTrue(contract["migration"]["externalRuntimeEndpointSupported"])
        self.assertTrue(contract["migration"]["sourceExtractionComplete"])
        self.assertEqual("teldigi5-wq/aetheris-ai-runtime", contract["certifiedRuntime"]["repository"])
        self.assertEqual(EXPECTED_RUNTIME_SHA, contract["certifiedRuntime"]["revision"])

    def test_default_compose_uses_exact_destination_runtime_not_local_source(self) -> None:
        compose = read(DEFAULT_COMPOSE)
        self.assertNotIn("build: ./orchestrator-service", compose)
        self.assertIn(
            f"https://github.com/teldigi5-wq/aetheris-ai-runtime.git#{EXPECTED_RUNTIME_SHA}:orchestrator-service",
            compose,
        )
        self.assertIn(
            "AETHERIS_ORCHESTRATOR_URI: ${AETHERIS_ORCHESTRATOR_URI:-http://orchestrator-service:8090}",
            compose,
        )

    def test_external_integration_compose_is_image_only(self) -> None:
        text = read(EXTERNAL_COMPOSE)
        section = text.split("  orchestrator-service:", 1)[1].split("\n  gateway:", 1)[0]
        self.assertIn("image: ${AETHERIS_AI_RUNTIME_IMAGE:?AETHERIS_AI_RUNTIME_IMAGE must be an exact-revision image}", section)
        self.assertNotIn("build:", section)

    def test_destination_runtime_certification_is_exact(self) -> None:
        reference = json.loads(read(CERTIFICATION_PATH))
        destination = reference["destination_runtime"]
        self.assertEqual("DESTINATION_RUNTIME_CERTIFIED", reference["status"])
        self.assertEqual("SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION", reference["source_root_deletion_status"])
        self.assertFalse(reference["platform_runtime_source_present"])
        self.assertEqual("teldigi5-wq/aetheris-ai-runtime", destination["repository"])
        self.assertEqual(EXPECTED_RUNTIME_SHA, destination["certified_sha"])
        self.assertEqual("6_OF_6_SUCCESS", destination["canonical_ci_status"])
        self.assertEqual(
            "c02ce146d52b816b0327d68a73f9366f11d4a1a5e3db2af492aaf92a338edbd0",
            destination["image_archive_sha256"],
        )


if __name__ == "__main__":
    unittest.main()
