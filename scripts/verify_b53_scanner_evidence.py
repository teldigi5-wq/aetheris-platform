#!/usr/bin/env python3
"""B53: fail-closed, offline CycloneDX/Grype evidence and release NO_GO gate.

Never claims deployment approval. Reads ONLY trusted local CI-generated SBOM,
Grype JSON and public vulnerability DB status, not credentials or live systems.
Exit 0: scan complete, requires human release review; 1: critical/high match
or stale DB => release NO_GO; 2: invalid/missing evidence => release NO_GO.
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

MODULES = frozenset({"gateway", "identity-service", "user-service", "audit-service"})
MAX_SCAN_SIZE = 32 * 1024 * 1024
MAX_DB_DAYS = 7


def load_json(filename):
    path = Path(filename)
    if not path.is_file() or not 2 < path.stat().st_size <= MAX_SCAN_SIZE:
        raise ValueError("missing or oversized scan evidence")
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def fresh_db(status, now):
    if not isinstance(status, dict):
        raise ValueError("malformed vulnerability DB report")
    db = status.get("db", status)
    if not isinstance(db, dict):
        raise ValueError("malformed DB payload")
    built = db.get("built")
    if not isinstance(built, str):
        raise ValueError("missing vulnerability DB built timestamp")
    try:
        date = datetime.fromisoformat(built.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("malformed vulnerability DB timestamp") from exc
    if date.tzinfo is None:
        raise ValueError("DB timestamp must be timezone-aware")
    age = (now - date.astimezone(timezone.utc)).total_seconds()
    return -300 <= age <= MAX_DB_DAYS * 86400


def assess(module, sbom, report, db_status, now=None):
    if module not in MODULES:
        raise ValueError("unrecognized service")
    now = datetime.now(timezone.utc) if now is None else now
    if not isinstance(sbom, dict) or sbom.get("bomFormat") != "CycloneDX":
        raise ValueError("not a CycloneDX JSON SBOM")
    components = sbom.get("components")
    if not isinstance(components, list) or len(components) < 10:
        raise ValueError("unacceptably empty SBOM")
    if any(not isinstance(c, dict) or not c.get("name") for c in components):
        raise ValueError("invalid SBOM package component")
    if not isinstance(report, dict) or not isinstance(report.get("matches"), list):
        raise ValueError("Grype scan has no match list")
    if not isinstance(report.get("descriptor"), dict):
        raise ValueError("untrusted or incomplete Grype scan header")
    blocking = 0
    reviewed = 0
    for match in report["matches"]:
        if not isinstance(match, dict):
            raise ValueError("unrecognized Grype match")
        vuln = match.get("vulnerability")
        artifact = match.get("artifact")
        if (not isinstance(vuln, dict) or not isinstance(artifact, dict)
                or not vuln.get("id") or not artifact.get("name")):
            raise ValueError("malformed vulnerability evidence")
        severity = vuln.get("severity")
        if not isinstance(severity, str) or severity.upper() not in {
            "CRITICAL", "HIGH", "MEDIUM", "LOW", "NEGLIGIBLE", "UNKNOWN"
        }:
            raise ValueError("invalid severity")
        reviewed += 1
        if severity.upper() in {"CRITICAL", "HIGH", "UNKNOWN"}:
            blocking += 1
    if not fresh_db(db_status, now):
        return "NO_GO_STALE_DB", len(components), reviewed, blocking
    if blocking:
        return "NO_GO_HIGH_CRITICAL_OR_UNRATED", len(components), reviewed, blocking
    return "REVIEW_SCANNED_NO_HIGH_CRITICAL", len(components), reviewed, 0


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 4 or args[0] not in MODULES:
        print("B53_INVALID_SCAN_EVIDENCE")
        return 2
    try:
        decision, packages, matches, blocked = assess(
            args[0], load_json(args[1]), load_json(args[2]), load_json(args[3])
        )
    except (OSError, ValueError, TypeError, UnicodeError, RecursionError):
        print("B53_INVALID_SCAN_EVIDENCE")
        return 2
    # Counts-only output. Actual advisory IDs/versions remain in private CI artifact.
    print(f"B53_{decision} service={args[0]} components={packages} matches={matches} blockers={blocked}")
    return 0 if decision.startswith("REVIEW_") else 1


if __name__ == "__main__":
    raise SystemExit(main())
