import importlib.util
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "check_ai_runtime_certification_drift.py"
SPEC = importlib.util.spec_from_file_location("runtime_drift", MODULE_PATH)
runtime_drift = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runtime_drift)


class RuntimeCertificationDriftTest(unittest.TestCase):
    def setUp(self):
        self.reference = {
            "destination_runtime": {
                "repository": "teldigi5-wq/aetheris-ai-runtime",
                "branch": "main",
                "certified_sha": "certified",
            },
            "runtime_owned_roots": [
                "orchestrator-service",
                "aetheris-quant",
                "aetheris-reasoning",
                "workstation-agent",
            ],
            "truth_boundaries": {
                "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
                "production_deployment_claim": False,
                "registry_publication_claim": False,
                "live_money_execution_claim": False,
            },
        }

    def test_exact_checkpoint_is_aligned(self):
        report = runtime_drift.build_report(self.reference, "certified", None)
        self.assertEqual("ALIGNED", report["status"])
        self.assertFalse(report["promotion_required"])
        self.assertFalse(report["review_recommended"])

    def test_docs_license_and_dependabot_only_are_non_runtime_drift(self):
        compare = {
            "status": "ahead",
            "ahead_by": 3,
            "behind_by": 0,
            "files": [
                {"filename": "README.md"},
                {"filename": "LICENSE"},
                {"filename": ".github/dependabot.yml"},
            ],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("NON_RUNTIME_DRIFT", report["status"])
        self.assertFalse(report["promotion_required"])

    def test_workflow_or_test_only_changes_are_visible_without_false_invalidation(self):
        compare = {
            "status": "ahead",
            "ahead_by": 2,
            "behind_by": 0,
            "files": [
                {"filename": ".github/workflows/ai-runtime-build.yml"},
                {"filename": "tests/test_runtime_contract.py"},
            ],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("EVIDENCE_PIPELINE_DRIFT", report["status"])
        self.assertFalse(report["promotion_required"])
        self.assertTrue(report["review_recommended"])

    def test_runtime_owned_source_change_requires_deliberate_promotion(self):
        compare = {
            "status": "ahead",
            "ahead_by": 1,
            "behind_by": 0,
            "files": [{"filename": "orchestrator-service/src/main/java/io/aetheris/Foo.java"}],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("RUNTIME_PROMOTION_REQUIRED", report["status"])
        self.assertTrue(report["promotion_required"])
        self.assertEqual(
            ["orchestrator-service/src/main/java/io/aetheris/Foo.java"],
            report["categories"]["runtime_source"],
        )

    def test_runtime_configuration_change_requires_deliberate_promotion(self):
        compare = {
            "status": "ahead",
            "ahead_by": 1,
            "behind_by": 0,
            "files": [{"filename": "configs/runtime-policy.json"}],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("RUNTIME_PROMOTION_REQUIRED", report["status"])
        self.assertTrue(report["promotion_required"])

    def test_diverged_or_rewritten_history_fails_closed(self):
        compare = {
            "status": "diverged",
            "ahead_by": 2,
            "behind_by": 1,
            "files": [{"filename": "README.md"}],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("CERTIFICATION_HISTORY_INVALID", report["status"])
        self.assertTrue(report["promotion_required"])
        self.assertTrue(report["review_recommended"])

    def test_physical_and_production_truth_boundaries_are_carried_into_report(self):
        compare = {
            "status": "ahead",
            "ahead_by": 1,
            "behind_by": 0,
            "files": [{"filename": "README.md"}],
        }
        report = runtime_drift.build_report(self.reference, "newer", compare)
        self.assertEqual("BLOCKED_PENDING_HARDWARE", report["physical_pc_status"])
        self.assertFalse(report["production_deployment_claim"])
        self.assertFalse(report["registry_publication_claim"])
        self.assertFalse(report["live_money_execution_claim"])


if __name__ == "__main__":
    unittest.main()
