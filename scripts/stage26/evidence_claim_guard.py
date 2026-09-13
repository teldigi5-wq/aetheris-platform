#!/usr/bin/env python3
"""Stage 26 evidence-integrity and pre-PC truth guard.

This guard protects public documentation and CI policy from silently converting
repository/CI evidence into claims about the owner's still-unvalidated physical PC.
It uses only the Python standard library.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POLICY = ROOT / "configs" / "stage26-evidence-policy.json"


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def check(policy: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    checks: list[dict[str, str]] = []

    def record(name: str, passed: bool, detail: str) -> None:
        checks.append({"check": name, "status": "PASS" if passed else "FAIL", "detail": detail})
        if not passed:
            errors.append(f"{name}: {detail}")

    record("schema-version", policy.get("schema_version") == 1, f"value={policy.get('schema_version')!r}")
    record("stage-number", policy.get("stage") == 26, f"value={policy.get('stage')!r}")
    record(
        "physical-status",
        policy.get("physical_pc_status") == "BLOCKED_PENDING_HARDWARE",
        f"value={policy.get('physical_pc_status')!r}",
    )

    boundaries = policy.get("hard_boundaries", {})
    for key, value in sorted(boundaries.items()):
        record(f"hard-boundary:{key}", value is False, f"value={value!r}")

    expected_hash = str(policy.get("stage23_expected_contract_sha256", "")).strip()
    stage23_file = ROOT / "scripts" / "stage23" / "expected-contract.sha256"
    actual_hash = stage23_file.read_text(encoding="utf-8").strip() if stage23_file.is_file() else "<missing>"
    record(
        "stage23-contract-freeze",
        actual_hash == expected_hash,
        f"expected={expected_hash}; actual={actual_hash}",
    )

    for item in policy.get("required_claims", []):
        relative = item["path"]
        path = ROOT / relative
        if not path.is_file():
            record(f"required-claims:{relative}", False, "file missing")
            continue
        text = path.read_text(encoding="utf-8")
        missing = [needle for needle in item.get("contains", []) if needle not in text]
        record(
            f"required-claims:{relative}",
            not missing,
            "all required truth markers present" if not missing else f"missing={missing}",
        )

    forbidden_claims = [str(value) for value in policy.get("forbidden_public_claims_before_hardware", [])]
    for relative in policy.get("public_truth_files", []):
        path = ROOT / relative
        if not path.is_file():
            record(f"public-truth-file:{relative}", False, "file missing")
            continue
        text_lower = path.read_text(encoding="utf-8").lower()
        hits = [claim for claim in forbidden_claims if claim.lower() in text_lower]
        record(
            f"public-truth-file:{relative}",
            not hits,
            "no prohibited physical-success claim" if not hits else f"prohibited={hits}",
        )

    workflow_dir = ROOT / ".github" / "workflows"
    workflow_files = sorted([*workflow_dir.glob("*.yml"), *workflow_dir.glob("*.yaml")])
    escape_hatches = [str(value).lower() for value in policy.get("forbidden_workflow_escape_hatches", [])]
    workflow_hits: list[str] = []
    for path in workflow_files:
        text_lower = path.read_text(encoding="utf-8").lower()
        for marker in escape_hatches:
            if marker in text_lower:
                workflow_hits.append(f"{path.relative_to(ROOT).as_posix()}::{marker}")
    record(
        "workflow-fail-closed",
        not workflow_hits,
        "no forbidden CI escape hatch" if not workflow_hits else f"found={workflow_hits}",
    )

    readme = ROOT / "README.md"
    readme_text = readme.read_text(encoding="utf-8") if readme.is_file() else ""
    required_readme_markers = (
        "BLOCKED_PENDING_HARDWARE",
        "Physical-PC validation remains pending",
        "Stage 26",
    )
    missing_readme = [value for value in required_readme_markers if value not in readme_text]
    record(
        "readme-truth-boundary",
        not missing_readme,
        "public README truth boundary present" if not missing_readme else f"missing={missing_readme}",
    )

    return {
        "schemaVersion": 1,
        "stage": 26,
        "status": "PASS" if not errors else "FAIL",
        "repositoryStatus": policy.get("repository_status"),
        "physicalPcStatus": policy.get("physical_pc_status"),
        "hardBoundaries": boundaries,
        "checks": checks,
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = check(load_json(args.policy))
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
