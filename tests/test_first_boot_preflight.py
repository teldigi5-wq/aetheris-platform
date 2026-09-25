import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "first_boot_preflight.py"
SPEC = importlib.util.spec_from_file_location("first_boot_preflight", MODULE_PATH)
preflight = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(preflight)

EXPECTED_RUNTIME_SHA = "65a6262717adcd52ac8d8a16ed6f223e299fd74d"
EXPECTED_RUNTIME_ARCHIVE_SHA256 = "18d934cee0ac61bb1065cfd8c104c4b91d10bad6d1d745c5ff72e86c03e0f14c"


class FirstBootPreflightTests(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads((ROOT / "configs" / "first-boot-contract.json").read_text(encoding="utf-8"))
        self.reference = json.loads((ROOT / "architecture" / "ai-runtime-certification-reference.json").read_text(encoding="utf-8"))

    def test_contract_schema_is_stage_25(self):
        self.assertEqual(self.contract["schema_version"], 1)
        self.assertEqual(self.contract["stage"], 25)
        self.assertIn("physical PC", self.contract["truth_boundary"])
        self.assertEqual(self.contract["foundation_scope"], "aetheris-platform-external-ai-runtime-v1")

    def test_platform_contract_does_not_require_runtime_source_roots(self):
        required = set(self.contract["required_repository_files"])
        self.assertNotIn("orchestrator-service/pom.xml", required)
        self.assertNotIn("workstation-agent/pom.xml", required)
        self.assertNotIn("aetheris-quant/.python-version", required)
        self.assertIn("architecture/ai-runtime-certification-reference.json", required)
        self.assertIn("docker-compose.core.yml", required)
        self.assertIn("docker-compose.integration-external.yml", required)
        self.assertIn("scripts/stage22/release_gate.py", required)
        self.assertIn("scripts/stage23/contract_guard.py", required)

    def test_runtime_source_roots_are_actually_absent(self):
        for root in ("orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent"):
            self.assertFalse((ROOT / root).exists(), root)

    def test_static_repository_contract_passes(self):
        checks = preflight.static_checks(self.contract)
        failures = [item for item in checks if item["status"] != "PASS"]
        self.assertEqual(failures, [], failures)

    def test_ci_report_never_claims_physical_validation(self):
        report = preflight.summary(preflight.static_checks(self.contract), "ci", self.contract)
        self.assertEqual(report["physical_pc_status"], "NOT_TESTED")
        self.assertEqual(report["foundation_scope"], "aetheris-platform-external-ai-runtime-v1")
        self.assertEqual(report["external_ai_runtime_repository"], "teldigi5-wq/aetheris-ai-runtime")
        self.assertEqual(report["external_ai_runtime_certified_sha"], EXPECTED_RUNTIME_SHA)
        self.assertEqual(report["status"], "PASS")

    def test_external_runtime_reference_is_exact_certified_and_extracted(self):
        destination = self.reference["destination_runtime"]
        self.assertEqual(self.reference["status"], "DESTINATION_RUNTIME_CERTIFIED")
        self.assertEqual(destination["repository"], "teldigi5-wq/aetheris-ai-runtime")
        self.assertEqual(destination["certified_sha"], EXPECTED_RUNTIME_SHA)
        self.assertEqual(destination["canonical_ci_status"], "6_OF_6_SUCCESS")
        self.assertEqual(self.reference["source_root_deletion_status"], "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION")
        self.assertFalse(self.reference["platform_runtime_source_present"])
        self.assertEqual(destination["image_archive_sha256"], EXPECTED_RUNTIME_ARCHIVE_SHA256)

    def test_external_compose_uses_runtime_image_not_local_build(self):
        text = (ROOT / "docker-compose.integration-external.yml").read_text(encoding="utf-8")
        self.assertIn("image: ${AETHERIS_AI_RUNTIME_IMAGE:?AETHERIS_AI_RUNTIME_IMAGE must be an exact-revision image}", text)
        self.assertIn("AETHERIS_ORCHESTRATOR_URI: ${AETHERIS_ORCHESTRATOR_URI:-http://orchestrator-service:8090}", text)
        self.assertNotIn("build: ./orchestrator-service", text)
        self.assertNotIn("build: ./workstation-agent", text)
        self.assertNotIn("build: ./aetheris-quant", text)
        self.assertNotIn("build: ./aetheris-reasoning", text)

    def test_forbidden_claims_are_explicit(self):
        claims = self.contract["forbidden_claims_before_physical_validation"]
        self.assertGreaterEqual(len(claims), 6)
        self.assertTrue(any("GPU" in claim for claim in claims))
        self.assertTrue(any("Docker Desktop" in claim for claim in claims))
        self.assertTrue(any("workstation agent" in claim for claim in claims))


if __name__ == "__main__":
    unittest.main()
