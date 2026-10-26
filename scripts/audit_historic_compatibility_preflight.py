#!/usr/bin/env python3
"""B4Z: offline-only composite Audit restored-schema and Flyway history triage.

Input: privately produced B4W schema (contract 2) and B4X history
(contract 1) JSON boolean reports. No DB/network access or row data.
Never authorizes migration/baseline/production; every valid report exits 1.
"""
import json
import sys
from pathlib import Path

from scripts.audit_ownership_preflight import classify as schema_classify
from scripts.audit_flyway_history_preflight import classify as history_classify

MAX_REPORT_BYTES = 16384


def _no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate metadata keys")
        result[key] = value
    return result


def load_report(path):
    file = Path(path)
    if not file.is_file() or file.stat().st_size > MAX_REPORT_BYTES:
        raise ValueError("invalid local report size")
    with file.open("r", encoding="utf-8") as stream:
        return json.load(stream, object_pairs_hook=_no_duplicate_keys)


def classify(schema_report, history_report):
    # Validate BOTH independently. A "bad schema" cannot conceal a malformed
    # history payload, or vice versa; malformed input is always exit 2.
    schema_status = schema_classify(schema_report)
    history_status = history_classify(history_report)
    if schema_status != "REVIEW_HISTORY_CONTENT_AND_OWNERSHIP":
        return "NO_GO_SCHEMA_OR_OWNERSHIP"
    if history_status != "REVIEW_FLYWAY_VALIDATE_CHECKSUM_AND_OWNERSHIP":
        return "NO_GO_HISTORY_CONTENT"
    return "REVIEW_PRIVATE_HISTORIC_RESTORE_AND_REAL_FLYWAY_VALIDATION"


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print("AUDIT_COMPAT_INVALID_INPUT")
        return 2
    try:
        status = classify(load_report(args[0]), load_report(args[1]))
    except (OSError, ValueError, TypeError, UnicodeError, RecursionError):
        print("AUDIT_COMPAT_INVALID_INPUT")
        return 2
    print("AUDIT_COMPAT_" + status)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
