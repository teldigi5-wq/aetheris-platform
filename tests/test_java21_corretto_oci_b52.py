"""B52: exact official patched Corretto OCI manifest pins, no security relaxation."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JDK = ("docker.io/library/amazoncorretto:21.0.13-al2023@sha256:"
       "1a307926702559b0086a5c4d339eaafa9c992b3e8ba54201ac8ebbde31d22f0b")
RUNTIME = ("docker.io/library/amazoncorretto:21.0.13-al2023-headless@sha256:"
           "212faf5f8f93d8f4854a3d89528834d11ad2d685de80721d4d24ac2fdd3e379e")
SERVICES = ("gateway", "identity-service", "user-service", "audit-service")


class CorrettoDockerfileTests(unittest.TestCase):
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

    def test_no_stale_or_floating_tags(self):
        for service in SERVICES:
            text = (ROOT / service / "Dockerfile").read_text()
            self.assertNotIn("21.0.12", text)
            self.assertNotIn("eclipse-temurin:", text)
            self.assertEqual(sum(x.startswith("FROM ") for x in text.splitlines()), 2)

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
            text = (ROOT / service / "Dockerfile").read_text()
            self.assertIn("ARG MAVEN_VERSION=3.9.11", text)
            self.assertIn("ARG MAVEN_SHA512=", text)
            self.assertIn("sha512sum --check --strict", text)
            self.assertIn("ENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]", text)


if __name__ == "__main__":
    unittest.main()
