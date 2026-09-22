#!/usr/bin/env python3
"""Verify the Step D AI-runtime extraction boundary and emit reproducible evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "architecture" / "ai-runtime-extraction-manifest.json"
EVIDENCE_DIR = ROOT / "build-evidence" / "architecture"
EXPECTED_DESTINATION = "teldigi5-wq/aetheris-ai-runtime"
EXPECTED_CORE_MODULES = {"gateway", "user-service", "identity-service", "audit-service"}


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def tracked_files(root: str) -> list[Path]:
    raw = subprocess.check_output(
        ["git", "ls-files", "-z", "--", root], cwd=ROOT
    )
    rows = [item.decode("utf-8") for item in raw.split(b"\0") if item]
    return [ROOT / row for row in rows]


def validate_root_pom(errors: list[str]) -> list[str]:
    pom = ROOT / "pom.xml"
    if not pom.is_file():
        errors.append("missing root pom.xml")
        return []
    try:
        tree = ET.parse(pom)
    except ET.ParseError as exc:
        errors.append(f"root pom.xml is not valid XML: {exc}")
        return []
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [
        node.text.strip()
        for node in tree.findall("m:modules/m:module", namespace)
        if node.text and node.text.strip()
    ]
    if set(modules) != EXPECTED_CORE_MODULES:
        errors.append(
            f"root Maven reactor must contain only core modules {sorted(EXPECTED_CORE_MODULES)}, got {modules}"
        )
    return modules


def require_paths(paths: list[str], label: str, errors: list[str]) -> None:
    for relative in paths:
        path = ROOT / relative
        if not path.exists():
            errors.append(f"missing {label}: {relative}")


def validate_review_patterns(patterns: list[str], errors: list[str]) -> dict[str, list[str]]:
    resolved: dict[str, list[str]] = {}
    for pattern in patterns:
        matches = sorted(
            str(path.relative_to(ROOT)).replace("\\", "/")
            for path in ROOT.glob(pattern)
        )
        resolved[pattern] = matches
        if not matches:
            errors.append(f"ownership review pattern matched nothing: {pattern}")
    return resolved


def build_inventory(move_roots: list[str], errors: list[str]) -> tuple[dict[str, dict[str, object]], list[str]]:
    inventory: dict[str, dict[str, object]] = {}
    digest_rows: list[str] = []
    for root in move_roots:
        path = ROOT / root
        if not path.is_dir():
            errors.append(f"missing AI-runtime source root: {root}")
            continue
        if path.is_symlink():
            errors.append(f"AI-runtime source root must not be a symlink: {root}")
            continue
        files = tracked_files(root)
        if not files:
            errors.append(f"AI-runtime source root has no tracked files: {root}")
            continue
        total_bytes = 0
        root_hasher = hashlib.sha256()
        for file_path in files:
            relative = str(file_path.relative_to(ROOT)).replace("\\", "/")
            if not file_path.is_file():
                errors.append(f"tracked extraction file is missing from checkout: {relative}")
                continue
            data = file_path.read_bytes()
            digest = sha256_bytes(data)
            total_bytes += len(data)
            digest_rows.append(f"{digest}  {relative}")
            root_hasher.update(relative.encode("utf-8"))
            root_hasher.update(b"\0")
            root_hasher.update(digest.encode("ascii"))
            root_hasher.update(b"\n")
        inventory[root] = {
            "tracked_file_count": len(files),
            "tracked_bytes": total_bytes,
            "inventory_sha256": root_hasher.hexdigest(),
        }
    return inventory, digest_rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--mode",
        choices=("pre-extraction", "platform-source-absent"),
        default="pre-extraction",
    )
    args = parser.parse_args()

    errors: list[str] = []
    if not MANIFEST_PATH.is_file():
        print(f"missing manifest: {MANIFEST_PATH}", file=sys.stderr)
        return 1

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    move_roots = list(manifest.get("move_roots", []))
    core_roots = list(manifest.get("core_roots", []))

    if manifest.get("schema_version") != 1:
        errors.append("manifest schema_version must be 1")
    if manifest.get("destination_repository") != EXPECTED_DESTINATION:
        errors.append(
            f"destination_repository must remain {EXPECTED_DESTINATION!r} until Issue #140 changes it explicitly"
        )
    if manifest.get("status") != "BLOCKED_PENDING_DESTINATION_REPOSITORY":
        errors.append("manifest must remain blocked until the destination repository exists")
    if len(move_roots) != 4 or len(set(move_roots)) != 4:
        errors.append(f"move_roots must contain exactly four unique roots, got {move_roots}")
    overlap = sorted(set(move_roots) & set(core_roots))
    if overlap:
        errors.append(f"core and AI-runtime ownership roots overlap: {overlap}")

    require_paths(manifest.get("transfer_workflows", []), "transfer workflow", errors)
    require_paths(manifest.get("copy_bootstrap", []), "bootstrap dependency", errors)
    require_paths(
        manifest.get("platform_retained_contract_assets", []),
        "platform-retained contract asset",
        errors,
    )
    review_matches = validate_review_patterns(
        manifest.get("ownership_review_before_final_removal", []), errors
    )
    root_modules = validate_root_pom(errors)

    inventory: dict[str, dict[str, object]] = {}
    digest_rows: list[str] = []
    if args.mode == "pre-extraction":
        inventory, digest_rows = build_inventory(move_roots, errors)
    else:
        present = [root for root in move_roots if (ROOT / root).exists()]
        if present:
            errors.append(f"AI-runtime source roots still present after simulated extraction: {present}")

    revision = git("rev-parse", "HEAD")
    baseline = manifest.get("source_certified_sha", "")
    if len(baseline) != 40 or any(ch not in "0123456789abcdef" for ch in baseline):
        errors.append(f"source_certified_sha is not a lowercase 40-character SHA: {baseline!r}")

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    report_name = (
        "ai-runtime-extraction-manifest-report.json"
        if args.mode == "pre-extraction"
        else "ai-runtime-extraction-source-absent-report.json"
    )
    report = {
        "status": "PASS" if not errors else "FAIL",
        "mode": args.mode,
        "revision": revision,
        "source_certified_sha": baseline,
        "destination_repository": manifest.get("destination_repository"),
        "migration_status": manifest.get("status"),
        "move_roots": move_roots,
        "core_roots": core_roots,
        "root_maven_modules": root_modules,
        "inventory": inventory,
        "ownership_review_matches": review_matches,
        "manifest_sha256": sha256_bytes(MANIFEST_PATH.read_bytes()),
        "truth_boundaries": manifest.get("truth_boundaries", {}),
        "errors": errors,
    }
    (EVIDENCE_DIR / report_name).write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    if args.mode == "pre-extraction":
        (EVIDENCE_DIR / "ai-runtime-extraction-sha256.txt").write_text(
            "\n".join(sorted(digest_rows)) + "\n", encoding="utf-8"
        )

    if errors:
        print("AI-runtime extraction manifest verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    if args.mode == "pre-extraction":
        total_files = sum(int(row["tracked_file_count"]) for row in inventory.values())
        total_bytes = sum(int(row["tracked_bytes"]) for row in inventory.values())
        print(
            f"AI-runtime extraction manifest: PASS ({total_files} tracked files, {total_bytes} bytes)"
        )
    else:
        print("AI-runtime extraction source-absent platform check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
