"""Keep all four core containers on the reviewed vendor-patched Temurin release.

This source guard is only a pin check; the Docker CI job independently inspects
the actual runtime truststores and verifies both negative and positive TLS.
"""
from __future__ import annotations

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("gateway", "identity-service", "user-service", "audit-service")
JDK = (
    "eclipse-temurin:21.0.13_9-jdk-noble@sha256:"
    "114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d"
)
JRE = (
    "eclipse-temurin:21.0.13_9-jre-noble@sha256:"
    "0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f"
)


class JavaContainerTrustTests(unittest.TestCase):
    def test_all_four_services_use_exact_patched_vendor_image_indices(self):
        for service in SERVICES:
            with self.subTest(service=service):
                content = (ROOT / service / "Dockerfile").read_text(encoding="utf-8")
                stages = re.findall(r"(?m)^FROM ([^\r\n]+)$", content)
                self.assertEqual([JDK + " AS build", JRE], stages)
                self.assertNotIn("amazoncorretto", content.lower())
                self.assertNotIn("21.0.12_8", content)

    def test_maven_checksum_and_non_root_runtime_are_preserved(self):
        for service in SERVICES:
            with self.subTest(service=service):
                content = (ROOT / service / "Dockerfile").read_text(encoding="utf-8")
                self.assertIn("ARG MAVEN_VERSION=3.9.11", content)
                self.assertIn("ARG MAVEN_SHA512=", content)
                self.assertIn("sha512sum --check --strict", content)
                self.assertIn("USER 10001:10001", content)
                self.assertIn("COPY --from=build --chown=10001:10001", content)
                self.assertEqual(2, len(re.findall(r"(?m)^FROM ", content)))


if __name__ == "__main__":
    unittest.main()
