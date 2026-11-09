"""Guard isolated Java container trust mitigation without changing application logic."""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("gateway", "identity-service", "user-service", "audit-service")
JDK = (
    "docker.io/library/amazoncorretto:21.0.13-al2023@sha256:"
    "1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b"
)
JRE = (
    "docker.io/library/amazoncorretto:21.0.13-al2023-headless@sha256:"
    "212faf5f8f93d8f4854a3d89528834d11ad2d685de80721d4d24ac2fdd3e379e"
)
COMPROMISED_ROOT_FINGERPRINTS = (
    "40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367",
    "1AA0C2709E831BD6E3B5129A00BA41F7EEEF020872F1E6504BF0F6C3F24F3AF3",
    "86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B",
    "D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4",
)


class JavaContainerTrustTests(unittest.TestCase):
    def test_every_build_and_runtime_stage_is_consistently_pinned(self):
        for service in SERVICES:
            with self.subTest(service=service):
                contents = (ROOT / service / "Dockerfile").read_text(encoding="utf-8")
                stages = re.findall(r"(?m)^FROM ([^\r\n]+)$", contents)
                self.assertEqual([JDK + " AS build", JRE], stages)
                self.assertIn("USER 10001:10001", contents)
                self.assertIn("MAVEN_SHA512=", contents)
                self.assertIn("sha512sum --check --strict", contents)
                self.assertIn("2023.12.20261026", contents)

    def test_each_stage_removes_disallowed_aliases_and_fingerprints(self):
        for service in SERVICES:
            dockerfile = (ROOT / service / "Dockerfile").read_text(encoding="utf-8")
            stages = re.split(r"(?m)^FROM ", dockerfile)[1:]
            self.assertEqual(2, len(stages))
            for stage_number, stage in enumerate(stages):
                with self.subTest(service=service, stage=stage_number):
                    self.assertIn("keytool -delete -cacerts", stage)
                    self.assertIn("keytool -list -cacerts", stage)
                    for fingerprint in COMPROMISED_ROOT_FINGERPRINTS:
                        self.assertGreaterEqual(stage.count(fingerprint), 2)


if __name__ == "__main__":
    unittest.main()
