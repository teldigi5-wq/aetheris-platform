import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage26" / "safety_certification.py"
SPEC = importlib.util.spec_from_file_location("stage26_safety_certification", MODULE_PATH)
cert = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(cert)


class Stage26SafetyCertificationTests(unittest.TestCase):
    def setUp(self):
        self.config_path = ROOT / "configs" / "stage26" / "safety-scenarios.json"
        self.config = cert.load_config(self.config_path)

    def test_truth_boundaries_are_explicit(self):
        self.assertTrue(self.config["simulation_only"])
        self.assertFalse(self.config["live_side_effects"])
        self.assertEqual(self.config["physical_pc_status"], "NOT_TESTED")

    def test_real_repository_safety_scenarios_pass(self):
        report = cert.certify(self.config, config_path=self.config_path)
        self.assertEqual(report["certification"], "PASS", report)
        self.assertEqual(report["fail_count"], 0, report)
        self.assertGreaterEqual(report["scenario_count"], 10)
        self.assertFalse(report["external_action_attempted"])
        self.assertFalse(report["network_required"])
        self.assertFalse(report["shell_execution_required"])

    def test_evidence_is_byte_deterministic(self):
        report_a = cert.certify(self.config, config_path=self.config_path)
        report_b = cert.certify(self.config, config_path=self.config_path)
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            evidence_a = cert.write_evidence(report_a, Path(first))
            evidence_b = cert.write_evidence(report_b, Path(second))
            self.assertEqual(
                Path(evidence_a["report"]).read_bytes(),
                Path(evidence_b["report"]).read_bytes(),
            )
            self.assertEqual(
                Path(evidence_a["manifest"]).read_bytes(),
                Path(evidence_b["manifest"]).read_bytes(),
            )
            self.assertEqual(evidence_a["report_sha256"], evidence_b["report_sha256"])
            self.assertEqual(evidence_a["manifest_sha256"], evidence_b["manifest_sha256"])

    def test_missing_required_marker_fails_closed(self):
        broken = copy.deepcopy(self.config)
        broken["scenarios"][0]["must_contain"].append("THIS_MARKER_MUST_NOT_EXIST_STAGE26")
        report = cert.certify(broken, config_path=self.config_path)
        self.assertEqual(report["certification"], "FAIL")
        self.assertGreater(report["fail_count"], 0)
        failed = next(item for item in report["results"] if item["status"] == "FAIL")
        self.assertIn("THIS_MARKER_MUST_NOT_EXIST_STAGE26", failed["missing_markers"])

    def test_repository_path_escape_is_rejected(self):
        broken = copy.deepcopy(self.config)
        broken["scenarios"][0]["file"] = "../../outside-aetheris.txt"
        with self.assertRaises(cert.CertificationError):
            cert.certify(broken, config_path=self.config_path)

    def test_duplicate_scenario_ids_are_rejected_by_loader(self):
        broken = copy.deepcopy(self.config)
        broken["scenarios"].append(copy.deepcopy(broken["scenarios"][0]))
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.json"
            path.write_text(json.dumps(broken), encoding="utf-8")
            with self.assertRaises(cert.CertificationError):
                cert.load_config(path)


if __name__ == "__main__":
    unittest.main()
