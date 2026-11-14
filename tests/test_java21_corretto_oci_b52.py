"""B52 (superseded 2026-11-12): exact patched Java OCI pins, no security relaxation.

B52 originally pinned Amazon Corretto 21.0.13 AL2023 images. Those images carried
Corretto 21.0.13.0.10, which does not resolve CVE-2026-114820, and had no eMudhra
trust proof. Canonical #412 replaced them with vendor-patched Eclipse Temurin
21.0.13+9 (see docs/security/B62_S0_I0_JAVA_IMAGE_RECONCILIATION_20261112.md).
These tests keep the B52 intent (exact digests, no floating tags, no downgrade)
and now also forbid the superseded Corretto build.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JDK = ("eclipse-temurin:21.0.13_9-jdk-noble@sha256:"
       "114cabb73033ca4ce7ceec3deb62396bc1b716282bae051ed3c1d82e38fcab2d")
RUNTIME = ("eclipse-temurin:21.0.13_9-jre-noble@sha256:"
           "0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f")
SERVICES = ("gateway", "identity-service", "user-service", "audit-service")


class PatchedJavaDockerfileTests(unittest.TestCase):
    def test_all_four_builder_pins_exact(self):
        for service in SERVICES:
            with self.subTest(service=service):
                text = (ROOT / service / "Dockerfile").read_text()
                self.assertEqual(text.count("FROM " + JDK + " AS build"), 1)

    def test_all_four_runtime_pins_exact(self):
        for service in SERVICES:
            with self.subTest(service=service):
                text = (ROOT / service / "Dockerfile").read_text()
                self.assertEqual(text.count("FROM " + RUNTIME + "\n"), 1)

    def test_no_stale_superseded_or_floating_tags(self):
        for service in SERVICES:
            with self.subTest(service=service):
                text = (ROOT / service / "Dockerfile").read_text()
                self.assertNotIn("21.0.12", text)
                self.assertNotIn("amazoncorretto", text.lower())
                self.assertNotIn(":latest", text)
                self.assertEqual(sum(x.startswith("FROM ") for x in text.splitlines()), 2)
                for line in text.splitlines():
                    if line.startswith("FROM "):
                        self.assertIn("@sha256:", line)

    def test_existing_non_root_security_preserved(self):
        for service in SERVICES:
            with self.subTest(service=service):
                text = (ROOT / service / "Dockerfile").read_text()
                self.assertIn("USER 10001:10001", text)
                self.assertIn("--uid 10001", text)
                self.assertIn("--gid 10001", text)
                self.assertIn("--chown=10001:10001", text)

    def test_maven_checksum_still_enforced(self):
        for service in SERVICES:
            with self.subTest(service=service):
                text = (ROOT / service / "Dockerfile").read_text()
                self.assertIn("ARG MAVEN_VERSION=3.9.11", text)
                self.assertIn("ARG MAVEN_SHA512=", text)
                self.assertIn("sha512sum --check --strict", text)
                self.assertIn("ENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]", text)


if __name__ == "__main__":
    unittest.main()
