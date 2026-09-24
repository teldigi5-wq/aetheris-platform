from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE_CONFIGS = (
    "gateway/src/main/resources/application.yml",
    "user-service/src/main/resources/application.yml",
    "identity-service/src/main/resources/application.yml",
    "audit-service/src/main/resources/application.yml",
)
EXPECTED = "show-details: ${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:never}"


class ActuatorHealthBoundaryTests(unittest.TestCase):
    def test_health_details_are_hidden_by_default_for_every_java_service(self) -> None:
        for relative in SERVICE_CONFIGS:
            with self.subTest(relative=relative):
                text = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn(EXPECTED, text)
                self.assertNotIn("show-details: always", text)

    def test_health_endpoint_remains_exposed_for_liveness_checks(self) -> None:
        for relative in SERVICE_CONFIGS:
            with self.subTest(relative=relative):
                text = (ROOT / relative).read_text(encoding="utf-8")
                self.assertIn("include: health", text)


if __name__ == "__main__":
    unittest.main()
