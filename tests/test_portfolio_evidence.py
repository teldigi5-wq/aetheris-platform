import json
from pathlib import Path
import tempfile
import unittest

from tools.validate_portfolio_evidence import DEFAULT_MANIFEST, load_manifest, validate


class PortfolioEvidenceTests(unittest.TestCase):
    def test_real_manifest_passes(self):
        report = validate(load_manifest(DEFAULT_MANIFEST))
        self.assertEqual("PASS", report["status"], report["errors"])
        self.assertGreaterEqual(report["claim_count"], 7)
        self.assertEqual("NOT_EVALUATED", report["runtime_claim"])
        self.assertEqual("BLOCKED_PENDING_HARDWARE", report["physical_pc_status"])

    def test_missing_evidence_fails_closed(self):
        manifest = load_manifest(DEFAULT_MANIFEST)
        manifest = json.loads(json.dumps(manifest))
        manifest["claims"][0]["evidence"][0]["path"] = "does/not/exist.txt"
        report = validate(manifest)
        self.assertEqual("FAIL", report["status"])
        self.assertTrue(any("missing evidence file" in error for error in report["errors"]))

    def test_missing_marker_fails_closed(self):
        manifest = load_manifest(DEFAULT_MANIFEST)
        manifest = json.loads(json.dumps(manifest))
        manifest["claims"][0]["evidence"][0]["contains"].append("definitely-not-present")
        report = validate(manifest)
        self.assertEqual("FAIL", report["status"])
        self.assertTrue(any("missing marker" in error for error in report["errors"]))

    def test_truth_boundary_is_required(self):
        manifest = load_manifest(DEFAULT_MANIFEST)
        manifest = json.loads(json.dumps(manifest))
        manifest["truth_boundary"] = "Repository evidence only."
        report = validate(manifest)
        self.assertEqual("FAIL", report["status"])
        self.assertTrue(any("truth_boundary missing marker" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
