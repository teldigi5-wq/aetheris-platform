"""No DB credentials needed to exercise the metadata-only comparator."""
import json
import tempfile
import unittest
from pathlib import Path

from scripts.schema_inventory import EXPECTED, evaluate_inventory, load_inventory


def valid_inventory():
    out = {"contract_version": 1, "tables": []}
    for name, spec in EXPECTED.items():
        cols = [
            {"name": col, "type": kind, "max_length": length,
             "not_null": required, "auto_generated": col == "id"}
            for col, (kind, length, required) in spec["columns"].items()
        ]
        item = dict(name=name, columns=cols, extra_column_count=0,
                    primary_id=False, unique_email=False,
                    unique_token_hash=False, refresh_account_fk=False,
                    named_token_index=False)
        for flag in spec["flags"]:
            item[flag] = True
        out["tables"].append(item)
    return out


class CatalogInventoryTests(unittest.TestCase):
    def test_valid_inventory(self):
        self.assertEqual(evaluate_inventory(valid_inventory()), [])

    def test_length_drift_rejected_without_private_values(self):
        data = valid_inventory()
        table = next(x for x in data["tables"] if x["name"] == "users")
        email = next(x for x in table["columns"] if x["name"] == "email")
        email["max_length"] = 180
        self.assertIn("users.email: type or nullability drift", evaluate_inventory(data))

    def test_missing_fk_and_named_index_are_rejected(self):
        data = valid_inventory()
        table = next(x for x in data["tables"]
                     if x["name"] == "identity_refresh_tokens")
        table["refresh_account_fk"] = False
        table["named_token_index"] = False
        issues = evaluate_inventory(data)
        self.assertIn("identity_refresh_tokens: missing refresh_account_fk", issues)
        self.assertIn("identity_refresh_tokens: missing named_token_index", issues)

    def test_unknown_tables_never_leak_name(self):
        data = valid_inventory()
        data["tables"][0]["name"] = "private_patient_or_customer_table"
        diagnostics = " ".join(evaluate_inventory(data))
        self.assertIn("unknown or duplicate table", diagnostics)
        self.assertNotIn("private_patient_or_customer_table", diagnostics)

    def test_disallows_extra_keys_or_row_values(self):
        data = valid_inventory()
        data["tables"][0]["rows"] = ["private.example"]
        self.assertIn("unexpected table fields", " ".join(evaluate_inventory(data)))

    def test_missing_table_fail_closed(self):
        data = valid_inventory()
        data["tables"].pop()
        self.assertTrue(any("missing table" in issue for issue in evaluate_inventory(data)))

    def test_rejects_oversized_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "inventory.json"
            path.write_text(json.dumps(valid_inventory()) + (" " * 66000))
            with self.assertRaises(ValueError):
                load_inventory(path)

    def test_sql_is_catalog_only_readonly_and_limits_names(self):
        path = Path("docs/database/rehearsal/catalog_inventory_readonly.sql")
        sql = path.read_text(encoding="utf-8")
        self.assertIn("BEGIN TRANSACTION READ ONLY;", sql)
        self.assertIn("FROM pg_class r", sql)
        self.assertIn("FROM pg_attribute", sql)
        self.assertIn("COMMIT;", sql)
        self.assertNotIn("FROM identity_accounts", sql)
        self.assertNotIn("FROM identity_refresh_tokens", sql)
        self.assertNotIn("FROM users", sql)
        self.assertNotIn("password_hash\n", sql)  # literal password values are absent


if __name__ == "__main__":
    unittest.main()
