"""B4W pure/offline negative controls: no data, Docker or credentials."""
import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.audit_ownership_preflight import BOOL_FIELDS, classify

ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / "docs/database/rehearsal/audit_ownership_inventory_readonly.sql"
SCRIPT = ROOT / "scripts/audit_ownership_preflight.py"

def fixture(**changes):
    result = {"contract_version": 2, **{k: False for k in BOOL_FIELDS}}
    result.update(changes)
    return result

class AuditOwnershipPreflightTests(unittest.TestCase):
    def test_empty_requires_review_not_go(self):
        self.assertEqual(classify(fixture()), "REVIEW_FRESH_DATABASE_AND_SERVICE_OWNERSHIP")

    def test_identity_shared_without_audit_history_is_no_go(self):
        self.assertEqual(classify(fixture(identity_accounts_exists=True)), "NO_GO_UNREVIEWED_SHARED_SCHEMA")

    def test_existing_audit_without_history_is_no_go(self):
        self.assertEqual(classify(fixture(audit_events_exists=True, audit_column_shape_ok=True,
                                          audit_primary_key_ok=True,
                                          audit_occurred_at_index_ok=True,
                                          audit_recorded_at_default_ok=True)), "NO_GO_AUDIT_HISTORY_ABSENT")

    def test_orphan_history_is_no_go(self):
        self.assertEqual(classify(fixture(audit_history_exists=True)), "NO_GO_ORPHAN_AUDIT_HISTORY")

    def test_bad_columns_even_with_history_is_no_go(self):
        self.assertEqual(classify(fixture(audit_events_exists=True, audit_history_exists=True,
                                          audit_primary_key_ok=True,
                                          audit_occurred_at_index_ok=True)), "NO_GO_AUDIT_SCHEMA_DRIFT")

    def test_existing_audit_and_history_still_requires_manual_review(self):
        self.assertEqual(classify(fixture(audit_events_exists=True, audit_history_exists=True,
                                          audit_column_shape_ok=True, audit_primary_key_ok=True,
                                          audit_occurred_at_index_ok=True,
                                          audit_recorded_at_default_ok=True)), "REVIEW_HISTORY_CONTENT_AND_OWNERSHIP")

    def test_missing_recorded_at_default_is_drift(self):
        good = fixture(audit_events_exists=True, audit_history_exists=True,
                       audit_column_shape_ok=True, audit_primary_key_ok=True,
                       audit_occurred_at_index_ok=True,
                       audit_recorded_at_default_ok=True)
        self.assertEqual(classify(good), "REVIEW_HISTORY_CONTENT_AND_OWNERSHIP")
        good["audit_recorded_at_default_ok"] = False
        self.assertEqual(classify(good), "NO_GO_AUDIT_SCHEMA_DRIFT")

    def test_wrong_index_direction_is_drift(self):
        good = fixture(audit_events_exists=True, audit_history_exists=True,
                       audit_column_shape_ok=True, audit_primary_key_ok=True,
                       audit_occurred_at_index_ok=True,
                       audit_recorded_at_default_ok=True)
        good["audit_occurred_at_index_ok"] = False
        self.assertEqual(classify(good), "NO_GO_AUDIT_SCHEMA_DRIFT")

    def test_rejects_extra_customer_data_and_malformed_input(self):
        for mutated in (
            {**fixture(), "customer_password": "PRIVATE_EXAMPLE"},
            {**fixture(), "contract_version": True},
            {**fixture(), "audit_history_exists": "true"},
            {"contract_version": 1},
            {**fixture(), "contract_version": 1},
            [],
        ):
            with self.subTest(value=type(mutated).__name__):
                with self.assertRaises(ValueError):
                    classify(mutated)

    def test_no_secrets_echo_and_never_zero_exit(self):
        with tempfile.TemporaryDirectory() as t:
            p = Path(t) / "inventory.json"
            p.write_text(json.dumps(fixture(identity_accounts_exists=True)))
            result = subprocess.run([sys.executable, str(SCRIPT), str(p)],
                                    text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 1)
            self.assertEqual(result.stdout.strip(), "AUDIT_INVENTORY_NO_GO_UNREVIEWED_SHARED_SCHEMA")
            p.write_text(json.dumps({**fixture(), "secret": "PRIVATE_EXAMPLE"}))
            result = subprocess.run([sys.executable, str(SCRIPT), str(p)],
                                    text=True, capture_output=True, check=False)
            self.assertEqual(result.returncode, 2)
            self.assertEqual(result.stdout.strip(), "AUDIT_INVENTORY_INVALID_INPUT")
            self.assertNotIn("PRIVATE_EXAMPLE", result.stdout + result.stderr)

    def test_query_metadata_only_and_read_only(self):
        sql = SQL.read_text()
        self.assertIn("BEGIN TRANSACTION READ ONLY;", sql)
        self.assertIn("pg_catalog.pg_attribute", sql)
        self.assertIn("pg_catalog.pg_constraint", sql)
        self.assertIn("pg_catalog.pg_index", sql)
        self.assertIn("pg_catalog.pg_attrdef", sql)
        self.assertIn("ix.indoption[0]", sql)
        self.assertIn("am.amname = 'btree'", sql)
        self.assertNotIn("FROM public.audit_events", sql)
        self.assertNotIn("FROM public.flyway_audit_schema_history", sql)
        self.assertNotIn("SELECT *", sql)
        self.assertNotIn("INSERT INTO", sql)
        self.assertNotIn("UPDATE public.", sql)
        self.assertNotIn("DELETE FROM", sql)

if __name__ == "__main__":
    unittest.main()
