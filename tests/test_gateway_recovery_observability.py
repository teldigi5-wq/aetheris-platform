"""No-network regression tests for redacted gateway restart diagnostics."""
from __future__ import annotations

import json
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.gateway_recovery_observability import allowlisted_state, capture_gateway_state


class GatewayRecoveryObservationTests(unittest.TestCase):
    def test_allowlist_excludes_unrelated_docker_fields(self):
        raw = {
            "Status": "exited",
            "Running": False,
            "ExitCode": 143,
            "OOMKilled": False,
            "Error": "internal environment content",
            "Health": {"Status": "unhealthy", "Log": [{"Output": "private request details"}]},
        }
        value = allowlisted_state(raw)
        self.assertEqual("exited", value["status"])
        self.assertEqual(143, value["exit_code"])
        self.assertEqual("unhealthy", value["health_status"])
        self.assertNotIn("environment", json.dumps(value))
        self.assertNotIn("request details", json.dumps(value))

    def test_missing_container_fails_to_healthy_claim(self):
        response = subprocess.CompletedProcess([], 0, "", "")
        with patch("tools.gateway_recovery_observability.subprocess.run", return_value=response):
            value = capture_gateway_state(Path("."))
        self.assertEqual({"available": False, "reason": "gateway_count_not_one"}, value)

    def test_inspect_failure_is_not_reported_as_running(self):
        results = [
            subprocess.CompletedProcess([], 0, "c" * 64 + "\n", ""),
            subprocess.CompletedProcess([], 1, "", "omitted"),
        ]
        with patch("tools.gateway_recovery_observability.subprocess.run", side_effect=results):
            value = capture_gateway_state(Path("."))
        self.assertEqual({"available": False, "reason": "inspect_failed"}, value)


if __name__ == "__main__":
    unittest.main()
