"""B4Z: adversarial offline schema+history compatibility evidence gate."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.audit_historic_compatibility_preflight import classify, load_report
from scripts.audit_ownership_preflight import BOOL_FIELDS as SCHEMA_BOOL_FIELDS
from scripts.audit_flyway_history_preflight import BOOL_FIELDS as HISTORY_BOOL_FIELDS

ROOT = Path(__file__).resolve().parents[1]
MODULE = "scripts.audit_historic_compatibility_preflight"


def schema_valid():
    v = {"contract_version": 2, **{k: False for k in SCHEMA_BOOL_FIELDS}}
    for k in ("audit_events_exists", "audit_history_exists",
              "audit_column_shape_ok", "audit_primary_key_ok",
              "audit_occurred_at_index_ok", "audit_recorded_at_default_ok"):
        v[k] = True
    return v


def history_valid():
    return {"contract_version": 1, **{k: True for k in HISTORY_BOOL_FIELDS}}


class AuditHistoricCompatibilityTests(unittest.TestCase):
    def test_healthy_metadata_still_never_grants_go(self):
        self.assertEqual(
            classify(schema_valid(), history_valid()),
            "REVIEW_PRIVATE_HISTORIC_RESTORE_AND_REAL_FLYWAY_VALIDATION"
        )

    def test_v1_schema_default_drift_denied_with_valid_history(self):
        x = schema_valid()
        x["audit_recorded_at_default_ok"] = False
        self.assertEqual(classify(x, history_valid()), "NO_GO_SCHEMA_OR_OWNERSHIP")

    def test_wrong_index_denied_even_with_valid_history(self):
        x = schema_valid()
        x["audit_occurred_at_index_ok"] = False
        self.assertEqual(classify(x, history_valid()), "NO_GO_SCHEMA_OR_OWNERSHIP")

    def test_existing_unreviewed_identity_user_schema_denied(self):
        x = schema_valid()
        x["audit_events_exists"] = False
        x["audit_history_exists"] = False
        x["users_exists"] = True
        self.assertEqual(classify(x, history_valid()), "NO_GO_SCHEMA_OR_OWNERSHIP")

    def test_baseline_and_failed_history_denied(self):
        for field in ("no_baseline_rows", "no_failed_rows",
                      "exact_v1_metadata", "no_unexpected_rows",
                      "checksum_present", "exactly_one_history_row"):
            h = history_valid()
            h[field] = False
            with self.subTest(field=field):
                self.assertEqual(classify(schema_valid(), h),
                                 "NO_GO_HISTORY_CONTENT")

    def test_unexpected_or_legacy_contracts_always_invalid(self):
        invalid_schema = [
            {"contract_version": 1, **{k: True for k in SCHEMA_BOOL_FIELDS}},
            {**schema_valid(), "account_row": "PRIVATE_MARKER"},
            {**schema_valid(), "audit_events_exists": "true"}
        ]
        for obj in invalid_schema:
            with self.subTest(obj="schema"):
                with self.assertRaises(ValueError):
                    classify(obj, history_valid())
        invalid_histories = [
            {**history_valid(), "contract_version": 2},
            {**history_valid(), "no_baseline_rows": "true"},
            {**history_valid(), "secret": "PRIVATE_MARKER"}
        ]
        for obj in invalid_histories:
            with self.subTest(obj="history"):
                with self.assertRaises(ValueError):
                    classify(schema_valid(), obj)

    def test_duplicate_json_keys_rejected_not_last_wins(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "flags.json"
            valid = json.dumps(schema_valid())
            p.write_text(valid.replace('"contract_version": 2',
                                       '"contract_version": 1, "contract_version": 2'))
            with self.assertRaises(ValueError):
                load_report(p)

    def test_oversized_and_missing_inputs_denied(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "report.json"
            p.write_bytes(b"x" * 17000)
            with self.assertRaises(ValueError):
                load_report(p)
            with self.assertRaises(ValueError):
                load_report(Path(tmp))
            with self.assertRaises(ValueError):
                load_report(Path(tmp) / "nonexistent")

    def test_cli_nonzero_and_no_private_data_or_raw_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp) / "schema.json", Path(tmp) / "history.json"
            a.write_text(json.dumps(schema_valid()))
            b.write_text(json.dumps(history_valid()))
            result = subprocess.run([sys.executable, "-m", MODULE, str(a), str(b)],
                                    cwd=ROOT, capture_output=True, text=True,
                                    check=False)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(result.stdout.strip(),
                             "AUDIT_COMPAT_REVIEW_PRIVATE_HISTORIC_RESTORE_AND_REAL_FLYWAY_VALIDATION")
            a.write_text(json.dumps({**schema_valid(), "password": "PRIVATE_MARKER"}))
            result = subprocess.run([sys.executable, "-m", MODULE, str(a), str(b)],
                                    cwd=ROOT, capture_output=True, text=True,
                                    check=False)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(result.stdout.strip(), "AUDIT_COMPAT_INVALID_INPUT")
            self.assertNotIn("PRIVATE_MARKER", result.stdout + result.stderr)

    def test_missing_or_extra_arguments_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "empty"
            p.write_text("null")
            for args in ([], [str(p)], [str(p)] * 3):
                result = subprocess.run([sys.executable, "-m", MODULE, *args],
                                        cwd=ROOT, capture_output=True, text=True,
                                        check=False)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout.strip(), "AUDIT_COMPAT_INVALID_INPUT")


if __name__ == "__main__":
    unittest.main()
