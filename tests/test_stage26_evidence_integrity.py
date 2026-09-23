import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_module(name: str, relative: str):
    path = ROOT / relative
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


guard = load_module("stage26_guard", "scripts/stage26/evidence_claim_guard.py")
catalog = load_module("stage26_catalog", "scripts/stage26/evidence_catalog.py")


class Stage26EvidenceIntegrityTests(unittest.TestCase):
    def setUp(self):
        self.policy = json.loads(
            (ROOT / "configs" / "stage26-evidence-policy.json").read_text(encoding="utf-8")
        )
        self.runtime = json.loads(
            (ROOT / "architecture" / "ai-runtime-certification-reference.json").read_text(encoding="utf-8")
        )

    def test_hard_boundaries_are_fail_closed(self):
        self.assertEqual(self.policy["physical_pc_status"], "BLOCKED_PENDING_HARDWARE")
        self.assertTrue(self.policy["hard_boundaries"])
        self.assertTrue(all(value is False for value in self.policy["hard_boundaries"].values()))

    def test_stage23_frozen_hash_is_preserved(self):
        expected = (ROOT / "scripts" / "stage23" / "expected-contract.sha256").read_text().strip()
        self.assertEqual(expected, self.policy["stage23_expected_contract_sha256"])

    def test_truth_guard_passes_repository_state(self):
        report = guard.check(self.policy)
        self.assertEqual(report["status"], "PASS", report["errors"])
        self.assertEqual(report["physicalPcStatus"], "BLOCKED_PENDING_HARDWARE")

    def test_catalog_is_deterministic_and_contains_no_physical_evidence(self):
        first = catalog.build(self.policy)
        second = catalog.build(self.policy)
        self.assertEqual(first, second)
        self.assertEqual(first["status"], "PASS")
        self.assertFalse(first["physicalEvidenceIncluded"])
        self.assertTrue(first["evidenceSources"])
        self.assertTrue(all(item["physicalMeasurement"] is False for item in first["evidenceSources"]))

    def test_runtime_safety_authority_is_pinned_to_destination(self):
        destination = self.runtime["destination_runtime"]
        self.assertEqual(self.runtime["status"], "DESTINATION_RUNTIME_CERTIFIED")
        self.assertEqual(destination["repository"], "teldigi5-wq/aetheris-ai-runtime")
        self.assertEqual(destination["certified_sha"], "68af39a1115a7330020c18b6e2cb601e66b8f22f")
        self.assertEqual(destination["canonical_ci_status"], "6_OF_6_SUCCESS")
        self.assertEqual(
            self.runtime["runtime_certifications"]["stage26_runtime_safety"],
            ".github/workflows/runtime-stage26-safety.yml",
        )
        self.assertEqual(
            self.runtime["source_root_deletion_status"],
            "BLOCKED_PENDING_EXTERNAL_INTEGRATION_PROOF",
        )

    def test_forbidden_claims_include_activation_and_hardware_success(self):
        claims = "\n".join(self.policy["forbidden_public_claims_before_hardware"])
        self.assertIn("productionActivationAllowed=true", claims)
        self.assertIn("Docker Desktop works on the owner's PC", claims)
        self.assertIn("GPU acceleration works on the owner's PC", claims)


if __name__ == "__main__":
    unittest.main()
