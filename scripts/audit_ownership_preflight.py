#!/usr/bin/env python3
"""B4W local-only, catalog-metadata-only Audit ownership triage.

Never authorizes deployment. Never connects to PostgreSQL or prints input,
identifiers, credentials, row data or raw inventory. Every valid state
remains operator-review-required or NO_GO (exit 1).
"""
import json
import sys
from pathlib import Path

FIELDS = frozenset({
    "contract_version", "audit_events_exists", "audit_history_exists",
    "identity_accounts_exists", "identity_refresh_tokens_exists", "users_exists",
    "audit_column_shape_ok", "audit_primary_key_ok", "audit_occurred_at_index_ok",
    "audit_recorded_at_default_ok",
})
BOOL_FIELDS = FIELDS - {"contract_version"}

def classify(data):
    """Classify a fixed contract without echoing private/untrusted values."""
    if (not isinstance(data, dict) or set(data) != FIELDS
            or type(data["contract_version"]) is not int
            or data["contract_version"] != 2
            or any(type(data[k]) is not bool for k in BOOL_FIELDS)):
        raise ValueError("invalid metadata contract")

    a, h = data["audit_events_exists"], data["audit_history_exists"]
    if a and not (data["audit_column_shape_ok"]
                  and data["audit_primary_key_ok"]
                  and data["audit_occurred_at_index_ok"]
                  and data["audit_recorded_at_default_ok"]):
        return "NO_GO_AUDIT_SCHEMA_DRIFT"
    if a and not h:
        return "NO_GO_AUDIT_HISTORY_ABSENT"
    if h and not a:
        return "NO_GO_ORPHAN_AUDIT_HISTORY"
    if a and h:
        # History presence alone is NOT evidence of valid V1/checksum/ownership.
        return "REVIEW_HISTORY_CONTENT_AND_OWNERSHIP"
    if any(data[k] for k in (
            "identity_accounts_exists", "identity_refresh_tokens_exists",
            "users_exists")):
        return "NO_GO_UNREVIEWED_SHARED_SCHEMA"
    return "REVIEW_FRESH_DATABASE_AND_SERVICE_OWNERSHIP"

def load(path):
    p = Path(path)
    if p.stat().st_size > 16384:
        raise ValueError("oversized metadata")
    with p.open("r", encoding="utf-8") as f:
        return json.load(f)

def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1:
        print("AUDIT_INVENTORY_INVALID_INPUT")
        return 2
    try:
        outcome = classify(load(argv[0]))
    except (ValueError, OSError, UnicodeError, TypeError):
        print("AUDIT_INVENTORY_INVALID_INPUT")
        return 2
    print("AUDIT_INVENTORY_" + outcome)
    return 1  # Intentionally NEVER a release-pass (zero) exit status.

if __name__ == "__main__":
    sys.exit(main())
