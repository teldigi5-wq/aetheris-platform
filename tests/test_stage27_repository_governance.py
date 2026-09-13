import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage27" / "repository_governance.py"
SPEC = importlib.util.spec_from_file_location("stage27_repository_governance", MODULE_PATH)
gov = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(gov)


class Stage27RepositoryGovernanceTests(unittest.TestCase):
    def setUp(self):
        self.config_path = ROOT / "configs" / "stage27" / "repository-governance.json"
        self.config = gov.load_config(self.config_path)
        self.canonical = self.config["canonical_development_branch"]
        self.stable = self.config["stable_branch"]

    def test_policy_has_single_canonical_development_line(self):
        self.assertEqual(self.stable, "main")
        self.assertEqual(self.canonical, "feature/syntra-aetheris-foundation-v2")
        self.assertTrue(self.config["release_to_main_requires_review"])
        self.assertFalse(self.config["direct_stage_branch_development_allowed"])
        self.assertEqual(self.config["allowed_main_pr_heads"], [self.canonical])

    def test_real_repository_has_no_legacy_workflow_trigger_reference(self):
        self.assertEqual(gov.scan_legacy_workflow_references(self.config), [])

    def test_canonical_push_passes(self):
        report = gov.build_report(
            self.config,
            event_name="push",
            ref_name=self.canonical,
            head_ref="",
            base_ref="",
        )
        self.assertEqual(report["status"], "PASS", report)
        self.assertTrue(report["promotionAllowedByContext"])

    def test_canonical_pull_request_to_main_passes(self):
        report = gov.build_report(
            self.config,
            event_name="pull_request",
            ref_name="12/merge",
            head_ref=self.canonical,
            base_ref=self.stable,
        )
        self.assertEqual(report["status"], "PASS", report)
        self.assertTrue(report["promotionAllowedByContext"])

    def test_legacy_branch_pull_request_to_main_fails_closed(self):
        legacy = self.config["legacy_branches"][0]
        report = gov.build_report(
            self.config,
            event_name="pull_request",
            ref_name="99/merge",
            head_ref=legacy,
            base_ref=self.stable,
        )
        self.assertEqual(report["status"], "FAIL")
        self.assertFalse(report["promotionAllowedByContext"])
        self.assertTrue(any("legacy branch" in error for error in report["errors"]))

    def test_workflow_reference_to_legacy_branch_is_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / ".github" / "workflows").mkdir(parents=True)
            (root / ".github" / "workflows" / "bad.yml").write_text(
                "on:\n  push:\n    branches: [ stage25-first-boot-readiness ]\n",
                encoding="utf-8",
            )
            hits = gov.scan_legacy_workflow_references(self.config, root)
            self.assertEqual(len(hits), 1)
            self.assertEqual(hits[0]["legacy_branch"], "stage25-first-boot-readiness")

    def test_config_rejects_canonical_branch_marked_legacy(self):
        broken = json.loads(json.dumps(self.config))
        broken["legacy_branches"].append(self.canonical)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.json"
            path.write_text(json.dumps(broken), encoding="utf-8")
            with self.assertRaises(gov.GovernanceError):
                gov.load_config(path)


if __name__ == "__main__":
    unittest.main()
