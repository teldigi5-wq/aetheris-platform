"""B51: local-only OCI provenance, runtime patch and anti-forgery regressions."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.verify_java21_oci_inventory import check_image, verify, read_report

ROOT = Path(__file__).resolve().parents[1]


def record(role):
    return {
        "publisher": "docker.io/library/eclipse-temurin",
        "tag": f"21.0.13_9-{role}-noble",
        "digest": "sha256:" + ("a" if role == "jdk" else "b") * 64,
        "java_version": 'openjdk version "21.0.13" 2026-10-20\nOpenJDK Runtime Environment Temurin-21.0.13+9',
        "platforms": ["linux/amd64", "linux/arm64"],
    }


def good():
    return {"jdk": record("jdk"), "jre": record("jre")}


class Java21OciInventoryTests(unittest.TestCase):
    def test_valid_evidence_is_review_only_and_immutable(self):
        ref = verify(good())
        for role in ("jdk", "jre"):
            self.assertIn("@sha256:", ref[role])
            self.assertIn("21.0.13_9", ref[role])

    def test_outdated_java_21_0_12_is_denied(self):
        for role in ("jdk", "jre"):
            doc = good()
            doc[role]["java_version"] = 'openjdk version "21.0.12"'
            with self.assertRaises(ValueError):
                verify(doc)

    def test_rejects_mixed_patch_versions(self):
        doc = good()
        doc["jre"]["java_version"] = 'openjdk version "21.0.14"'
        with self.assertRaises(ValueError):
            verify(doc)

    def test_untrusted_registry_or_mutable_only_reference_denied(self):
        for field, value in (("publisher", "example.net/eclipse-temurin"),
                             ("digest", "sha256:abc"), ("digest", ""),
                             ("tag", "latest"), ("tag", "21.0.12_8-jdk-noble")):
            item = record("jdk")
            item[field] = value
            with self.subTest(field=field, value=value):
                with self.assertRaises(ValueError):
                    check_image("jdk", item)

    def test_architecture_coverage_denied_if_incomplete_or_duplicated(self):
        for platforms in (["linux/amd64"], ["linux/amd64", "linux/amd64"],
                          ["linux/amd64", "linux/ppc64le"], ["linux/amd64", None]):
            d = good()
            d["jdk"]["platforms"] = platforms
            with self.subTest(platforms=platforms):
                with self.assertRaises((ValueError, TypeError)):
                    verify(d)

    def test_rejects_unexpected_metadata_and_missing_roles(self):
        for input_data in ({"jdk": record("jdk")}, {"jdk": record("jdk"), "jre": record("jre"), "extra": {}},
                           {"jdk": "not-an-object", "jre": record("jre")}):
            with self.assertRaises(ValueError):
                verify(input_data)
        d = good()
        d["jdk"]["password"] = "PRIVATE_MARKER"
        with self.assertRaises(ValueError):
            verify(d)

    def test_no_private_input_echo_for_invalid_documents(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "candidate.json"
            d = good()
            d["jdk"]["secret"] = "DO_NOT_ECHO_SECRET"
            file.write_text(json.dumps(d))
            proc = subprocess.run(
                [sys.executable, "-m", "scripts.verify_java21_oci_inventory", str(file)],
                cwd=ROOT, text=True, capture_output=True, check=False
            )
            self.assertEqual(proc.returncode, 2)
            self.assertEqual(proc.stdout.strip(), "JAVA21_OCI_INVALID_EVIDENCE")
            self.assertNotIn("DO_NOT_ECHO_SECRET", proc.stdout + proc.stderr)

    def test_duplicate_keys_and_oversize_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "candidate.json"
            file.write_text('{"jdk":{}, "jdk":{}, "jre":{}}')
            with self.assertRaises(ValueError):
                read_report(file)
            file.write_bytes(b"x" * 8193)
            with self.assertRaises(ValueError):
                read_report(file)

    def test_all_four_original_dockerfiles_remain_unchanged(self):
        for module in ("gateway", "user-service", "identity-service", "audit-service"):
            text = (ROOT / module / "Dockerfile").read_text()
            self.assertIn("eclipse-temurin:21.0.12_8-jdk-noble@sha256:", text)
            self.assertIn("eclipse-temurin:21.0.12_8-jre-noble@sha256:", text)
            self.assertIn("USER 10001:10001", text)
            self.assertIn("ARG MAVEN_SHA512=", text)


if __name__ == "__main__":
    unittest.main()
