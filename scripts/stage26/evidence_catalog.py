#!/usr/bin/env python3
"""Generate a deterministic Stage 26 evidence-source catalog."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_POLICY = ROOT / "configs" / "stage26-evidence-policy.json"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def build(policy: dict[str, Any]) -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    missing: list[str] = []
    for relative in sorted(set(policy.get("evidence_sources", []))):
        path = ROOT / relative
        if not path.is_file():
            missing.append(relative)
            continue
        entries.append(
            {
                "path": relative,
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
                "evidenceClass": "REPOSITORY_OR_CI_SOURCE",
                "physicalMeasurement": False,
            }
        )

    return {
        "schemaVersion": 1,
        "stage": 26,
        "repositoryStatus": policy.get("repository_status"),
        "physicalPcStatus": policy.get("physical_pc_status"),
        "physicalEvidenceIncluded": False,
        "hardBoundaries": policy.get("hard_boundaries", {}),
        "stage23ExpectedContractSha256": policy.get("stage23_expected_contract_sha256"),
        "evidenceSources": entries,
        "missingSources": missing,
        "status": "PASS" if not missing else "FAIL",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "stage26" / "evidence-catalog.json")
    args = parser.parse_args()

    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    payload = build(policy)
    rendered = json.dumps(payload, indent=2, sort_keys=True) + "\n"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    digest = hashlib.sha256(json.dumps(payload, separators=(",", ":"), sort_keys=True).encode()).hexdigest()
    print(f"STAGE26_EVIDENCE_CATALOG_SHA256={digest}")
    print(rendered, end="")
    return 0 if payload["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
