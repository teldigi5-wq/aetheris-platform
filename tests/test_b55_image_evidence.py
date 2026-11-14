"""B55: source / image ID / CycloneDX and Grype artifact binding negative tests."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.verify_b55_image_evidence import MODULES, make_record, verify_records, strict_json

ROOT = Path(__file__).resolve().parents[1]
SOURCE = "a" * 40


def fixtures(folder):
    p = Path(folder)
    for idx, service in enumerate(MODULES):
        archive = p / (service + ".tar")
        sbom = p / (service + ".cyclonedx.json")
        grype = p / (service + ".grype.json")
        archive.write_bytes(("synthetic-in-memory-docker-fixture:" + service).encode())
        sbom.write_text(json.dumps({
            "bomFormat": "CycloneDX",
            "components": [{"name": "invented-" + service + "-" + str(i)}
                           for i in range(11)]}), encoding="utf-8")
        grype.write_text(json.dumps({"descriptor": {"name": "grype"},
                                     "matches": []}), encoding="utf-8")
        rec = make_record(service, "sha256:" + hex(idx + 1)[2:].rjust(64, "0"),
                          archive, sbom, grype, SOURCE)
        (p / (service + ".evidence.json")).write_text(json.dumps(rec), encoding="utf-8")
    return p


class B55EvidenceTests(unittest.TestCase):
    def test_all_four_valid_records_match_exact_source_and_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(len(verify_records(fixtures(tmp), SOURCE)), 4)

    def test_any_tampered_sbom_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            target = root / "user-service.cyclonedx.json"
            target.write_text(target.read_text().replace("invented", "changed"))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_any_tampered_grype_report_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            target = root / "gateway.grype.json"
            target.write_text(json.dumps({"descriptor": {"name": "grype"},
                                          "matches": [{"vulnerability": {
                                              "id": "DO_NOT_CLAIM"} }]}))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_wrong_commit_or_service_does_not_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            with self.assertRaises(ValueError):
                verify_records(root, "b" * 40)
            target = root / "audit-service.evidence.json"
            rec = json.loads(target.read_text())
            rec["service"] = "gateway"
            target.write_text(json.dumps(rec))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_fake_unsigned_or_truncated_image_id_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            f = root / "identity-service.evidence.json"
            rec = json.loads(f.read_text())
            rec["image_id"] = "sha256:broken"
            f.write_text(json.dumps(rec))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_missing_report_or_missing_service_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            (root / "audit-service.grype.json").unlink()
            with self.assertRaises(OSError):
                verify_records(root, SOURCE)

    def test_duplicate_json_keys_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            f = root / "gateway.evidence.json"
            content = f.read_text()
            f.write_text(content.replace('"contract_version": 1',
                                         '"contract_version": 2, "contract_version": 1'))
            with self.assertRaises(ValueError):
                strict_json(f)

    def test_missing_counter_or_added_secret_metadata_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            f = root / "user-service.evidence.json"
            rec = json.loads(f.read_text())
            rec["credential"] = "DO_NOT_ECHO"
            f.write_text(json.dumps(rec))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_wrong_archive_digest_shape_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            f = root / "gateway.evidence.json"
            rec = json.loads(f.read_text())
            rec["docker_archive_sha256"] = "not-a-sha256"
            f.write_text(json.dumps(rec))
            with self.assertRaises(ValueError):
                verify_records(root, SOURCE)

    def test_cli_verify_fail_closed_fixed_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = fixtures(tmp)
            good = subprocess.run([sys.executable, "-m", "scripts.verify_b55_image_evidence",
                                   "verify", str(root), SOURCE], cwd=ROOT,
                                  text=True, capture_output=True)
            self.assertEqual(good.returncode, 0)
            self.assertIn("B55_FOUR_IMAGE_SCAN_HASHES_MATCH_CHECKED_OUT_COMMIT", good.stdout)
            (root / "audit-service.evidence.json").write_text('{"credential":"DO_NOT_ECHO"}')
            bad = subprocess.run([sys.executable, "-m", "scripts.verify_b55_image_evidence",
                                  "verify", str(root), SOURCE], cwd=ROOT,
                                 text=True, capture_output=True)
            self.assertEqual(bad.returncode, 2)
            self.assertEqual(bad.stdout.strip(), "B55_NO_GO_INVALID_IMAGE_EVIDENCE")
            self.assertNotIn("DO_NOT_ECHO", bad.stdout + bad.stderr)


if __name__ == "__main__":
    unittest.main()
