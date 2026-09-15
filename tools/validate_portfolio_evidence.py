#!/usr/bin/env python3
"""Validate recruiter-facing core-platform evidence without claiming runtime proof."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "build-evidence" / "portfolio" / "core-platform-evidence.json"
ALLOWED_MATURITY = {"CORE_IMPLEMENTED", "REPOSITORY_TESTED"}


def load_manifest(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def validate(manifest: dict) -> dict:
    errors: list[str] = []
    verified: list[dict] = []

    if manifest.get("schema_version") != 1:
        errors.append("schema_version must be 1")
    if manifest.get("scope") != "core-platform":
        errors.append("scope must be core-platform")

    truth_boundary = str(manifest.get("truth_boundary", ""))
    for required in ("Repository evidence", "Runtime", "physical-PC validation"):
        if required not in truth_boundary:
            errors.append(f"truth_boundary missing marker: {required}")

    claims = manifest.get("claims")
    if not isinstance(claims, list) or not claims:
        errors.append("claims must be a non-empty list")
        claims = []

    seen_ids: set[str] = set()
    for claim in claims:
        claim_id = str(claim.get("id", "")).strip()
        title = str(claim.get("title", "")).strip()
        maturity = str(claim.get("maturity", "")).strip()
        evidence = claim.get("evidence")

        if not claim_id:
            errors.append("claim missing id")
            continue
        if claim_id in seen_ids:
            errors.append(f"duplicate claim id: {claim_id}")
        seen_ids.add(claim_id)
        if not title:
            errors.append(f"{claim_id}: missing title")
        if maturity not in ALLOWED_MATURITY:
            errors.append(f"{claim_id}: unsupported maturity {maturity!r}")
        if not isinstance(evidence, list) or not evidence:
            errors.append(f"{claim_id}: evidence must be non-empty")
            continue

        checked_paths: list[str] = []
        for item in evidence:
            rel = str(item.get("path", "")).strip()
            kind = str(item.get("kind", "file")).strip()
            markers = item.get("contains", [])
            if not rel:
                errors.append(f"{claim_id}: evidence item missing path")
                continue
            path = (ROOT / rel).resolve()
            try:
                path.relative_to(ROOT.resolve())
            except ValueError:
                errors.append(f"{claim_id}: evidence path escapes repository: {rel}")
                continue

            if kind == "directory":
                if not path.is_dir():
                    errors.append(f"{claim_id}: missing evidence directory: {rel}")
                checked_paths.append(rel)
                continue
            if kind != "file":
                errors.append(f"{claim_id}: unsupported evidence kind {kind!r} for {rel}")
                continue
            if not path.is_file():
                errors.append(f"{claim_id}: missing evidence file: {rel}")
                continue
            if not isinstance(markers, list):
                errors.append(f"{claim_id}: contains must be a list for {rel}")
                continue

            text = path.read_text(encoding="utf-8")
            for marker in markers:
                marker = str(marker)
                if marker not in text:
                    errors.append(f"{claim_id}: {rel} missing marker {marker!r}")
            checked_paths.append(rel)

        verified.append({
            "id": claim_id,
            "maturity": maturity,
            "evidence_paths": sorted(checked_paths),
        })

    return {
        "schema_version": 1,
        "scope": "core-platform",
        "status": "PASS" if not errors else "FAIL",
        "claim_count": len(claims),
        "verified_claims": sorted(verified, key=lambda item: item["id"]),
        "errors": sorted(errors),
        "runtime_claim": "NOT_EVALUATED",
        "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    manifest = load_manifest(args.manifest)
    report = validate(manifest)
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    sys.stdout.write(rendered)
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
