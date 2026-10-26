#!/usr/bin/env python3
"""B4X: offline-only, fail-closed Audit Flyway history metadata triage.

The input must come from the paired read-only PostgreSQL query.
Never validates a migration checksum, connects to any DB, echoes source,
authorizes a baseline or returns a release-success (0) exit status.
"""
import json
import sys
from pathlib import Path

FIELDS = frozenset({
    "contract_version", "exactly_one_history_row", "exact_v1_metadata",
    "no_baseline_rows", "no_failed_rows", "no_unexpected_rows",
    "checksum_present",
})
BOOL_FIELDS = FIELDS - {"contract_version"}


def classify(payload):
    if (
        not isinstance(payload, dict)
        or set(payload) != FIELDS
        or type(payload.get("contract_version")) is not int
        or payload["contract_version"] != 1
        or any(type(payload[name]) is not bool for name in BOOL_FIELDS)
    ):
        raise ValueError("invalid inventory contract")

    if not payload["no_baseline_rows"]:
        return "NO_GO_BASELINE_ROW_REQUIRES_REVIEW"
    if not payload["no_failed_rows"]:
        return "NO_GO_FAILED_MIGRATION_ROW"
    if not payload["exactly_one_history_row"]:
        return "NO_GO_HISTORY_CARDINALITY"
    if not payload["no_unexpected_rows"] or not payload["exact_v1_metadata"]:
        return "NO_GO_UNEXPECTED_MIGRATION_METADATA"
    if not payload["checksum_present"]:
        return "NO_GO_CHECKSUM_ABSENT"
    return "REVIEW_FLYWAY_VALIDATE_CHECKSUM_AND_OWNERSHIP"


def read(path):
    file = Path(path)
    if file.stat().st_size > 8192:
        raise ValueError("oversized metadata")
    with file.open("r", encoding="utf-8") as stream:
        return json.load(stream)


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print("AUDIT_HISTORY_INVALID_INPUT")
        return 2
    try:
        decision = classify(read(args[0]))
    except (ValueError, OSError, UnicodeError, TypeError, RecursionError):
        print("AUDIT_HISTORY_INVALID_INPUT")
        return 2
    print("AUDIT_HISTORY_" + decision)
    return 1  # No acceptable operator-release outcome is offered by this tool.


if __name__ == "__main__":
    sys.exit(main())
