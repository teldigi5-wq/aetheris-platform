#!/usr/bin/env python3
"""Fail-closed, metadata-only Aetheris PostgreSQL schema drift analysis.

Input must be output of docs/database/rehearsal/catalog_inventory_readonly.sql.
No credentials, addresses, account rows, table contents or raw metadata are
printed by this program. It deliberately performs no database connections.
"""
import argparse
import json
from pathlib import Path

EXPECTED = {
    "identity_accounts": {
        "columns": {
            "id": ("bigint", None, True),
            "name": ("varchar", 100, True),
            "email": ("varchar", 180, True),
            "password_hash": ("varchar", 100, True),
            "role": ("varchar", 32, True),
        },
        "flags": ("primary_id", "unique_email"),
    },
    "identity_refresh_tokens": {
        "columns": {
            "id": ("bigint", None, True),
            "account_id": ("bigint", None, True),
            "token_hash": ("varchar", 64, True),
            "expires_at": ("timestamptz", None, True),
            "revoked": ("boolean", None, True),
            "created_at": ("timestamptz", None, True),
        },
        "flags": ("primary_id", "unique_token_hash",
                  "refresh_account_fk", "named_token_index"),
    },
    "users": {
        "columns": {
            "id": ("bigint", None, True),
            "name": ("varchar", 255, True),
            "email": ("varchar", 255, True),
        },
        "flags": ("primary_id", "unique_email"),
    },
}

ALLOWED_TABLE_KEYS = {
    "name", "columns", "extra_column_count", "primary_id", "unique_email",
    "unique_token_hash", "refresh_account_fk", "named_token_index",
}
ALLOWED_COL_KEYS = {"name", "type", "max_length", "not_null", "auto_generated"}


def evaluate_inventory(data):
    """Return safe, predefined diagnostic codes. Never echo untrusted values."""
    issues = []
    if not isinstance(data, dict) or set(data) != {"contract_version", "tables"}:
        return ["inventory-format: unexpected top-level keys"]
    if data["contract_version"] != 1 or not isinstance(data["tables"], list):
        return ["inventory-format: unsupported contract"]
    if len(data["tables"]) > len(EXPECTED):
        return ["inventory-format: unexpected table metadata"]
    seen = {}
    for table in data["tables"]:
        if (not isinstance(table, dict) or
                set(table) != ALLOWED_TABLE_KEYS or
                not isinstance(table.get("name"), str)):
            return ["inventory-format: unexpected table fields"]
        name = table["name"]
        if name not in EXPECTED or name in seen:
            return ["inventory-format: unknown or duplicate table"]
        seen[name] = table
    for name, contract in EXPECTED.items():
        if name not in seen:
            issues.append(f"{name}: missing table")
            continue
        table = seen[name]
        if type(table["extra_column_count"]) is not int or table["extra_column_count"] != 0:
            issues.append(f"{name}: unexpected extra columns")
        columns = table["columns"]
        if not isinstance(columns, list) or len(columns) > len(contract["columns"]):
            issues.append(f"{name}: invalid columns metadata")
            continue
        parsed = {}
        for col in columns:
            if (not isinstance(col, dict) or set(col) != ALLOWED_COL_KEYS or
                    col.get("name") not in contract["columns"]):
                issues.append(f"{name}: unexpected column metadata")
                continue
            key = col["name"]
            if key in parsed:
                issues.append(f"{name}: duplicate column metadata")
                continue
            parsed[key] = col
        for colname, (kind, max_len, not_null) in contract["columns"].items():
            if colname not in parsed:
                issues.append(f"{name}.{colname}: missing column")
                continue
            col = parsed[colname]
            if (col["type"] != kind or col["max_length"] != max_len or
                    type(col["not_null"]) is not bool or col["not_null"] is not not_null):
                issues.append(f"{name}.{colname}: type or nullability drift")
            if colname == "id" and col.get("auto_generated") is not True:
                issues.append(f"{name}.id: missing ID-generation mechanism")
        for flag in contract["flags"]:
            if table[flag] is not True:
                issues.append(f"{name}: missing {flag}")
        for flag in ALLOWED_TABLE_KEYS - {"name", "columns", "extra_column_count"}:
            if type(table[flag]) is not bool:
                issues.append(f"{name}: malformed constraint indicator")
                break
    return sorted(set(issues))


def load_inventory(path):
    file = Path(path)
    if file.stat().st_size > 65536:
        raise ValueError("metadata-only inventory exceeds allowed size")
    with file.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inventory", help="locally saved metadata-only catalog JSON")
    args = parser.parse_args()
    try:
        issues = evaluate_inventory(load_inventory(args.inventory))
    except (OSError, ValueError, UnicodeError):
        print("SCHEMA INVENTORY: INVALID INPUT")
        raise SystemExit(2)
    if issues:
        for item in issues:
            print(f"SCHEMA DRIFT: {item}")
        raise SystemExit(1)
    print("SCHEMA INVENTORY: PASS (expected tables and constraints; no row data checked)")


if __name__ == "__main__":
    main()
