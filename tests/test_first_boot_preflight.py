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


class FirstBootPreflightTests(unittest.TestCase):
    def setUp(self):
        self.contract = json.loads(
            (ROOT / "configs" / "first-boot-contract.json").read_text(encoding="utf-8")
        )

    def test_contract_schema_is_stage_25(self):
        self.assertEqual(self.contract["schema_version"], 1)
        self.assertEqual(self.contract["stage"], 25)
        self.assertIn("physical PC", self.contract["truth_boundary"])

    def test_full_foundation_is_in_contract(self):
        required = set(self.contract["required_repository_files"])
        self.assertIn("orchestrator-service/pom.xml", required)
        self.assertIn("workstation-agent/pom.xml", required)
        self.assertIn("scripts/stage22/release_gate.py", required)
        self.assertIn("scripts/stage23/contract_guard.py", required)
        self.assertIn(8090, self.contract["expected_local_ports"])

    def test_static_repository_contract_passes(self):
        checks = preflight.static_checks(self.contract)
        failures = [item for item in checks if item["status"] != "PASS"]
        self.assertEqual(failures, [], failures)

    def test_ci_report_never_claims_physical_validation(self):
        report = preflight.summary(preflight.static_checks(self.contract), "ci")
        self.assertEqual(report["physical_pc_status"], "NOT_TESTED")
        self.assertEqual(report["foundation_scope"], "syntra-aetheris-foundation-v2")
        self.assertEqual(report["status"], "PASS")

    def test_forbidden_claims_are_explicit(self):
        claims = self.contract["forbidden_claims_before_physical_validation"]
        self.assertGreaterEqual(len(claims), 6)
        self.assertTrue(any("GPU" in claim for claim in claims))
        self.assertTrue(any("Docker Desktop" in claim for claim in claims))
        self.assertTrue(any("workstation agent" in claim for claim in claims))


if __name__ == "__main__":
    unittest.main()
