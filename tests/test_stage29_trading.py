import importlib.util
import json
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage29" / "trading_intelligence.py"
POLICY_PATH = ROOT / "configs" / "stage29" / "trading-policy.json"
FIXTURES = ROOT / "tests" / "fixtures" / "stage29"

spec = importlib.util.spec_from_file_location("stage29_trading", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class Stage29TradingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = module.load_json(POLICY_PATH)

    def fixture(self, name):
        return module.load_json(FIXTURES / name)

    def test_policy_is_fail_closed(self):
        module.validate_policy(self.policy)
        self.assertFalse(self.policy["liveExecutionEnabled"])
        self.assertNotIn("LIVE", self.policy["allowedModes"])
        self.assertTrue(self.policy["requireExplicitApproval"])
        self.assertFalse(self.policy["networkRequiredForCertification"])
        self.assertFalse(self.policy["secretsRequiredForCertification"])

    def test_policy_rejects_live_enablement(self):
        bad = deepcopy(self.policy)
        bad["liveExecutionEnabled"] = True
        with self.assertRaises(module.PolicyError):
            module.validate_policy(bad)

    def test_policy_rejects_unsafe_leverage_ceiling(self):
        bad = deepcopy(self.policy)
        bad["maxLeverage"] = 25
        with self.assertRaises(module.PolicyError):
            module.validate_policy(bad)

    def test_approved_paper_setup_is_proposal_only(self):
        result = module.plan(self.policy, self.fixture("approved-paper.json"))
        self.assertEqual(result["status"], "PROPOSAL_READY")
        self.assertFalse(result["executionPermitted"])
        self.assertFalse(result["orderSubmitted"])
        self.assertTrue(result["requiresExplicitApproval"])
        self.assertEqual(result["rejectionReasons"], [])
        self.assertEqual(result["cappedLeverage"], 3)
        self.assertEqual(result["rewardRiskRatio"], "2.00000000")

    def test_low_score_rejected(self):
        result = module.plan(self.policy, self.fixture("low-score.json"))
        self.assertIn("SETUP_SCORE_BELOW_MINIMUM", result["rejectionReasons"])
        self.assertEqual(result["proposedNotional"], "0.00000000")

    def test_invalid_geometry_rejected(self):
        result = module.plan(self.policy, self.fixture("invalid-geometry.json"))
        self.assertIn("INVALID_TP_SL_GEOMETRY", result["rejectionReasons"])

    def test_daily_loss_lockout(self):
        result = module.plan(self.policy, self.fixture("daily-loss-lockout.json"))
        self.assertIn("DAILY_LOSS_LOCKOUT", result["rejectionReasons"])

    def test_open_position_lockout(self):
        result = module.plan(self.policy, self.fixture("open-position-lockout.json"))
        self.assertIn("OPEN_POSITION_LOCKOUT", result["rejectionReasons"])

    def test_live_mode_is_always_rejected(self):
        result = module.plan(self.policy, self.fixture("live-mode.json"))
        self.assertIn("LIVE_EXECUTION_BLOCKED_STAGE_29", result["rejectionReasons"])
        self.assertFalse(result["executionPermitted"])
        self.assertFalse(result["orderSubmitted"])

    def test_requested_leverage_is_capped_for_reporting(self):
        candidate = self.fixture("approved-paper.json")
        candidate["requestedLeverage"] = 10
        result = module.plan(self.policy, candidate)
        self.assertEqual(result["cappedLeverage"], 3)
        self.assertFalse(result["executionPermitted"])

    def test_deterministic_serialization(self):
        candidate = self.fixture("approved-paper.json")
        first = module.canonical_json(module.plan(self.policy, candidate))
        second = module.canonical_json(module.plan(self.policy, candidate))
        self.assertEqual(first.encode("utf-8"), second.encode("utf-8"))

    def test_cli_writes_deterministic_json(self):
        result = module.plan(self.policy, self.fixture("approved-paper.json"))
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "out.json"
            path.write_text(module.canonical_json(result), encoding="utf-8")
            parsed = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(parsed["stage"], 29)
            self.assertFalse(parsed["orderSubmitted"])


if __name__ == "__main__":
    unittest.main()
