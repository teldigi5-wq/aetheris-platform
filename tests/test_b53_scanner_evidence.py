"""B53 offline CycloneDX/Grype policy anti-false-green tests."""
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from scripts.verify_b53_scanner_evidence import assess, fresh_db, load_json

ROOT = Path(__file__).resolve().parents[1]
NOW = datetime(2026, 10, 26, 12, tzinfo=timezone.utc)


def sbom():
    return {"bomFormat": "CycloneDX", "specVersion": "1.6",
            "components": [{"type": "library", "name": "synthetic-pkg-" + str(i),
                            "version": "1.0"} for i in range(25)]}


def grype(severity=None):
    return {"descriptor": {"name": "grype", "version": "0.121.0"},
            "matches": [] if severity is None else [{
                "artifact": {"name": "invented-only"}, "vulnerability": {
                    "id": "CVE-2099-0001", "severity": severity
                }
            }]}


def db(days=0):
    return {"built": (NOW - timedelta(days=days)).isoformat()}


class ScannerEvidenceTests(unittest.TestCase):
    def test_clean_scan_only_yields_review_not_release_approved(self):
        decision, components, matches, blockers = assess("audit-service", sbom(), grype(), db(), NOW)
        self.assertEqual(decision, "REVIEW_SCANNED_NO_HIGH_CRITICAL")
        self.assertEqual((components, matches, blockers), (25, 0, 0))

    def test_high_critical_and_unrated_are_all_release_blockers(self):
        for severity in ("High", "Critical", "Unknown"):
            with self.subTest(severity=severity):
                d = assess("gateway", sbom(), grype(severity), db(), NOW)
                self.assertEqual(d[0], "NO_GO_HIGH_CRITICAL_OR_UNRATED")
                self.assertEqual(d[3], 1)

    def test_low_and_medium_are_not_hidden(self):
        for severity in ("Medium", "Low", "Negligible"):
            with self.subTest(severity=severity):
                result = assess("gateway", sbom(), grype(severity), db(), NOW)
                self.assertEqual(result[0], "REVIEW_SCANNED_NO_HIGH_CRITICAL")
                self.assertEqual(result[2], 1)

    def test_stale_database_denied_even_if_no_vulnerabilities(self):
        self.assertEqual(assess("gateway", sbom(), grype(), db(8), NOW)[0], "NO_GO_STALE_DB")
        self.assertTrue(fresh_db(db(7), NOW))

    def test_missing_malformed_database_timestamp_denied(self):
        for report in ({}, {"built": "yesterday"}, {"built": "2026-10-26T12:00:00"},
                       {"built": None}, {"db": {"built": 123}}):
            with self.subTest(report=report):
                with self.assertRaises(ValueError):
                    assess("gateway", sbom(), grype(), report, NOW)
        self.assertFalse(fresh_db({"built": (NOW+timedelta(days=1)).isoformat()}, NOW))

    def test_missing_or_fake_sbom_denied(self):
        for report in ({}, {"bomFormat": "NotCycloneDX", "components": sbom()["components"]},
                       {"bomFormat": "CycloneDX", "components": []},
                       {"bomFormat": "CycloneDX", "components": [{"name": "fake"}]}):
            with self.subTest(report=report):
                with self.assertRaises(ValueError):
                    assess("user-service", report, grype(), db(), NOW)

    def test_grype_scan_must_be_valid_and_not_empty_placeholder(self):
        for report in ({}, {"matches": []}, {"descriptor": {}, "matches": "none"},
                       {"descriptor": {}, "matches": [{}]},
                       grype("UNRECOGNIZED")):
            with self.subTest(report=report):
                with self.assertRaises(ValueError):
                    assess("user-service", sbom(), report, db(), NOW)

    def test_unknown_module_is_not_authorized(self):
        with self.assertRaises(ValueError):
            assess("production-db", sbom(), grype(), db(), NOW)

    def test_oversized_empty_and_missing_files_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            file = Path(d)/"scan.json"
            with self.assertRaises(ValueError):
                load_json(file)
            file.write_text("{}", encoding="utf-8")
            with self.assertRaises(ValueError):
                load_json(file)
            file.write_bytes(b" " * (32 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError):
                load_json(file)

    def test_cli_does_not_echo_source_or_claim_release_success(self):
        with tempfile.TemporaryDirectory() as d:
            a,b,c = (Path(d)/x for x in ("bom.json", "scan.json", "db.json"))
            a.write_text(json.dumps(sbom()), encoding="utf-8")
            b.write_text(json.dumps(grype("High")), encoding="utf-8")
            c.write_text(json.dumps({"built": datetime.now(timezone.utc).isoformat()}), encoding="utf-8")
            command=[sys.executable, "-m", "scripts.verify_b53_scanner_evidence",
                     "user-service", str(a), str(b), str(c)]
            result=subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 1)
            self.assertIn("B53_NO_GO_HIGH_CRITICAL_OR_UNRATED", result.stdout)
            self.assertNotIn("CVE-2099-0001", result.stdout + result.stderr)
            a.write_text('{"customer_secret":"DO_NOT_ECHO"}', encoding="utf-8")
            result=subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(result.stdout.strip(), "B53_INVALID_SCAN_EVIDENCE")
            self.assertNotIn("DO_NOT_ECHO", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
