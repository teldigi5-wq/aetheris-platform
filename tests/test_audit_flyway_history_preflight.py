"""B4X strict offline Audit history triage tests; all states never release GO."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.audit_flyway_history_preflight import BOOL_FIELDS, classify, read

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/audit_flyway_history_preflight.py"
SQL = ROOT / "docs/database/rehearsal/audit_flyway_history_readonly.sql"


def valid():
    return {"contract_version": 1, **{k: True for k in BOOL_FIELDS}}


class AuditFlywayHistoryTests(unittest.TestCase):
    def test_genuine_shape_still_requires_checksum_validation(self):
        self.assertEqual(classify(valid()), "REVIEW_FLYWAY_VALIDATE_CHECKSUM_AND_OWNERSHIP")

    def test_baseline_row_is_not_implicitly_accepted(self):
        x = valid()
        x["no_baseline_rows"] = False
        self.assertEqual(classify(x), "NO_GO_BASELINE_ROW_REQUIRES_REVIEW")

    def test_failed_row_denied(self):
        x = valid()
        x["no_failed_rows"] = False
        self.assertEqual(classify(x), "NO_GO_FAILED_MIGRATION_ROW")

    def test_zero_or_multiple_rows_denied(self):
        x = valid()
        x["exactly_one_history_row"] = False
        self.assertEqual(classify(x), "NO_GO_HISTORY_CARDINALITY")

    def test_unknown_v1_script_or_version_denied(self):
        for field in ("exact_v1_metadata", "no_unexpected_rows"):
            x = valid()
            x[field] = False
            self.assertEqual(classify(x), "NO_GO_UNEXPECTED_MIGRATION_METADATA")

    def test_missing_checksum_denied(self):
        x = valid()
        x["checksum_present"] = False
        self.assertEqual(classify(x), "NO_GO_CHECKSUM_ABSENT")

    def test_extra_fields_old_versions_and_untrusted_types_denied(self):
        examples = [
            {"contract_version": 2, **{k: True for k in BOOL_FIELDS}},
            {**valid(), "customer": "NEVER_ECHO_PRIVATE_INPUT"},
            {**valid(), "checksum_present": "true"},
            {**valid(), "contract_version": True},
            {"contract_version": 1},
            [],
            None,
        ]
        for x in examples:
            with self.subTest(value=type(x).__name__):
                with self.assertRaises(ValueError):
                    classify(x)

    def test_invalid_input_never_echoes_private_fields(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "history.json"
            path.write_text(json.dumps({**valid(), "secret": "NEVER_ECHO_PRIVATE_INPUT"}))
            p = subprocess.run(
                [sys.executable, str(SCRIPT), str(path)], check=False,
                capture_output=True, text=True
            )
            self.assertEqual(p.returncode, 2)
            self.assertEqual(p.stdout.strip(), "AUDIT_HISTORY_INVALID_INPUT")
            self.assertNotIn("NEVER_ECHO_PRIVATE_INPUT", p.stdout + p.stderr)

    def test_valid_input_never_returns_release_success(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "history.json"
            path.write_text(json.dumps(valid()))
            p = subprocess.run(
                [sys.executable, str(SCRIPT), str(path)], check=False,
                capture_output=True, text=True
            )
            self.assertEqual(p.returncode, 1)
            self.assertEqual(p.stdout.strip(), "AUDIT_HISTORY_REVIEW_FLYWAY_VALIDATE_CHECKSUM_AND_OWNERSHIP")
            path.write_text(json.dumps(valid()) + " " * 8500)
            with self.assertRaises(ValueError):
                read(path)

    def test_read_only_history_query_and_fixed_projection(self):
        sql = SQL.read_text(encoding="utf-8")
        self.assertIn("BEGIN TRANSACTION READ ONLY;", sql)
        self.assertIn("FROM public.flyway_audit_schema_history", sql)
        self.assertIn("V1__create_audit_events.sql", sql)
        self.assertIn("'checksum_present'", sql)
        self.assertNotIn("SELECT *", sql)
        self.assertNotIn("DELETE FROM", sql)
        self.assertNotIn("UPDATE public.", sql)
        self.assertNotIn("INSERT INTO", sql)
        self.assertNotIn("installed_on,", sql)
        self.assertNotIn("installed_by,", sql)
        self.assertNotIn("'checksum',", sql)


if __name__ == "__main__":
    unittest.main()
