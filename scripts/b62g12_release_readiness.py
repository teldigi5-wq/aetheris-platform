#!/usr/bin/env python3
"""B62-G12 offline, strictly fail-closed production-cutover review classifier.

DOES NOT authorize production, merge, database migration or emergency actions.
A green synthetic CI proof can only establish evidence for human review.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

EXPECTED_KEYS = frozenset({
    "contract_version", "scope", "synthetic_backup_restored",
    "fresh_revocations_preserved", "stale_restore_rejected",
    "three_network_authenticated_readers", "wrong_password_rejected",
    "reader_write_denied", "production_historical_migration_approved",
    "production_backup_restore_owner_approved",
    "production_workload_identities_approved",
    "production_emergency_writer_audit_approved",
    "production_replica_consistency_certified",
    "production_tenant_object_authorization_certified",
})
BOOL_KEYS = EXPECTED_KEYS - {"contract_version", "scope"}
SYNTHETIC = frozenset({
    "synthetic_backup_restored", "fresh_revocations_preserved",
    "stale_restore_rejected", "three_network_authenticated_readers",
    "wrong_password_rejected", "reader_write_denied",
})
PRODUCTION = BOOL_KEYS - SYNTHETIC
MAX_BYTES = 4096


def classify(payload: object) -> str:
    if (not isinstance(payload, dict) or set(payload) != EXPECTED_KEYS
        or type(payload.get("contract_version")) is not int
        or payload["contract_version"] != 1
        or payload.get("scope") != "B62G12_DISPOSABLE_CI_ONLY"
        or any(type(payload[k]) is not bool for k in BOOL_KEYS)):
        return "NO_GO_INVALID_OR_UNTRUSTED_EVIDENCE"
    if not all(payload[k] for k in SYNTHETIC):
        return "NO_GO_SYNTHETIC_PROOF_INCOMPLETE"
    # A local JSON document cannot establish owner signatures, role ownership,
    # migration checksums or a real deployment's provenance. Even if every
    # untrusted field claims approval, the tool MUST never return GO.
    if any(payload[k] for k in PRODUCTION):
        return "NO_GO_UNVERIFIED_PRODUCTION_ATTESTATION"
    return "NO_GO_REQUIRES_INDEPENDENT_OWNER_AND_RELEASE_APPROVAL"


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 1:
        print("B62G12_NO_GO_INVALID_INPUT")
        return 2
    try:
        file = Path(args[0])
        if not file.is_file() or file.stat().st_size > MAX_BYTES:
            raise ValueError("invalid evidence file")
        payload = json.loads(file.read_text(encoding="utf-8"))
        status = classify(payload)
    except (OSError, ValueError, UnicodeError, RecursionError, TypeError):
        status = "NO_GO_INVALID_OR_UNTRUSTED_EVIDENCE"
    print("B62G12_" + status)
    return 1  # No successful release-admission result is supported.


if __name__ == "__main__":
    raise SystemExit(main())
