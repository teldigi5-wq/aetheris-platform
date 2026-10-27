#!/usr/bin/env python3
"""Independent, fail-closed release closure checkpoint (review-only).

This reads source files, never credentials, GitHub tokens or user data.
A local repository and self-reported attestations NEVER establish production
approval. A successful --assert-blocked test means release is BLOCKED.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

COMPOSES = (
    "docker-compose.yml",
    "docker-compose.core.yml",
    "docker-compose.integration-external.yml",
)
BACKEND_FILTERS = (
    "user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
    "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java",
)
MAJOR_BLOCKERS = (
    "historical_migration_owner_approval",
    "production_backup_restore_evidence",
    "object_level_tenant_authorization_certification",
    "workload_identity_and_secret_rotation",
    "audited_emergency_dual_control",
    "revocation_primary_consistency_and_live_filter_integration",
    "supply_chain_dependency_and_image_provenance",
    "certificate_trust_physical_verification",
    "external_release_gate_certification",
)
VERSION = 1


def evaluate(root: Path) -> dict[str, object]:
    risk_files: list[str] = []
    for relative in COMPOSES:
        path = root / relative
        try:
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            risk_files.append(relative + ":UNVERIFIED_OR_MISSING")
            continue
        if re.search(r"(?m)^\s*SPRING_JPA_HIBERNATE_DDL_AUTO\s*:\s*['\"]?update\b", content):
            risk_files.append(relative + ":UNREVIEWED_AUTO_DDL")
        elif not re.search(r"(?m)^\s*SPRING_JPA_HIBERNATE_DDL_AUTO\s*:\s*['\"]?validate\b", content):
            risk_files.append(relative + ":MIGRATION_POLICY_NOT_PROVEN")
    missing_auth: list[str] = []
    for relative in BACKEND_FILTERS:
        path = root / relative
        try:
            src = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            missing_auth.append(relative + ":MISSING")
            continue
        # Source presence alone never proves genuine enforcement.
        if "parseSignedClaims" not in src or "chain.doFilter" not in src:
            missing_auth.append(relative + ":JWT_FILTER_UNVERIFIED")
    return {
        "contract_version": VERSION,
        "status": "NO_GO",
        "safe_to_merge_or_deploy": False,
        "unreviewed_schema_mutation": risk_files,
        "backend_service_authentication_gaps": missing_auth,
        "independent_release_attestations_required": list(MAJOR_BLOCKERS),
        "source_only_evidence_cannot_authorize_release": True,
        "production_actions_performed": False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--assert-blocked", action="store_true")
    parser.add_argument("--require-ready", action="store_true")
    parser.add_argument("--json-out", type=Path)
    args = parser.parse_args()
    result = evaluate(args.root)
    output = json.dumps(result, sort_keys=True, indent=2) + "\n"
    print(output, end="")
    if args.json_out:
        # Never allow writing outside a caller-chosen CI work directory via
        # source-provided automatic report destinations.
        args.json_out.write_text(output, encoding="utf-8")
    if args.require_ready:
        return 1  # NEVER release from source-only or self-asserted evidence.
    if args.assert_blocked:
        return 0 if result["status"] == "NO_GO" and not result["safe_to_merge_or_deploy"] else 1
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
