#!/usr/bin/env python3
"""Classify drift between the platform-certified AI runtime and live runtime main.

The guard is intentionally semantic rather than commit-count based:
- runtime-owned source or runtime configuration drift requires deliberate promotion;
- CI/test/script-only drift is surfaced for review but does not invalidate runtime source;
- docs/license/dependency-governance-only drift is informational.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REFERENCE = ROOT / "architecture" / "ai-runtime-certification-reference.json"
DEFAULT_OUTPUT = ROOT / "build" / "runtime-certification" / "drift-report.json"

PROMOTION_SENSITIVE_PREFIXES = ("architecture/", "configs/")
EVIDENCE_PIPELINE_PREFIXES = (".github/workflows/", "scripts/", "tests/")


def _github_json(url: str, token: str | None = None) -> dict[str, Any]:
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": "aetheris-runtime-certification-drift-audit",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def _runtime_owned(path: str, runtime_roots: list[str]) -> bool:
    return any(path == root or path.startswith(f"{root}/") for root in runtime_roots)


def classify_changed_paths(paths: list[str], runtime_roots: list[str]) -> tuple[str, dict[str, list[str]]]:
    categories: dict[str, list[str]] = {
        "runtime_source": [],
        "runtime_config": [],
        "evidence_pipeline": [],
        "non_runtime": [],
    }

    for path in sorted(set(paths)):
        if _runtime_owned(path, runtime_roots):
            categories["runtime_source"].append(path)
        elif path.startswith(PROMOTION_SENSITIVE_PREFIXES):
            categories["runtime_config"].append(path)
        elif path.startswith(EVIDENCE_PIPELINE_PREFIXES):
            categories["evidence_pipeline"].append(path)
        else:
            categories["non_runtime"].append(path)

    if categories["runtime_source"] or categories["runtime_config"]:
        return "RUNTIME_PROMOTION_REQUIRED", categories
    if categories["evidence_pipeline"]:
        return "EVIDENCE_PIPELINE_DRIFT", categories
    if paths:
        return "NON_RUNTIME_DRIFT", categories
    return "ALIGNED", categories


def build_report(reference: dict[str, Any], runtime_head: str, compare: dict[str, Any] | None) -> dict[str, Any]:
    destination = reference["destination_runtime"]
    certified_sha = destination["certified_sha"]
    runtime_roots = list(reference.get("runtime_owned_roots", []))

    base = {
        "repository": destination["repository"],
        "branch": destination.get("branch", "main"),
        "certified_sha": certified_sha,
        "runtime_head_sha": runtime_head,
        "physical_pc_status": reference.get("truth_boundaries", {}).get("physical_pc_status"),
        "production_deployment_claim": reference.get("truth_boundaries", {}).get("production_deployment_claim", False),
        "registry_publication_claim": reference.get("truth_boundaries", {}).get("registry_publication_claim", False),
        "live_money_execution_claim": reference.get("truth_boundaries", {}).get("live_money_execution_claim", False),
    }

    if runtime_head == certified_sha:
        return {
            **base,
            "status": "ALIGNED",
            "ahead_by": 0,
            "changed_files": [],
            "categories": {
                "runtime_source": [],
                "runtime_config": [],
                "evidence_pipeline": [],
                "non_runtime": [],
            },
            "promotion_required": False,
            "review_recommended": False,
        }

    if compare is None:
        raise ValueError("compare payload is required when runtime head differs from certified SHA")

    compare_status = compare.get("status")
    if compare_status not in {"ahead", "identical"}:
        return {
            **base,
            "status": "CERTIFICATION_HISTORY_INVALID",
            "compare_status": compare_status,
            "ahead_by": compare.get("ahead_by"),
            "behind_by": compare.get("behind_by"),
            "changed_files": [item.get("filename", "") for item in compare.get("files", [])],
            "categories": {},
            "promotion_required": True,
            "review_recommended": True,
        }

    changed_files = [item["filename"] for item in compare.get("files", []) if item.get("filename")]
    status, categories = classify_changed_paths(changed_files, runtime_roots)
    return {
        **base,
        "status": status,
        "compare_status": compare_status,
        "ahead_by": compare.get("ahead_by", 0),
        "behind_by": compare.get("behind_by", 0),
        "changed_files": changed_files,
        "categories": categories,
        "promotion_required": status in {"RUNTIME_PROMOTION_REQUIRED", "CERTIFICATION_HISTORY_INVALID"},
        "review_recommended": status in {"RUNTIME_PROMOTION_REQUIRED", "EVIDENCE_PIPELINE_DRIFT", "CERTIFICATION_HISTORY_INVALID"},
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, default=DEFAULT_REFERENCE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--runtime-head-sha", default="", help="Optional exact runtime main SHA for deterministic/offline use")
    parser.add_argument("--compare-json", type=Path, help="Optional saved GitHub compare payload for deterministic/offline use")
    args = parser.parse_args()

    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    destination = reference["destination_runtime"]
    repository = destination["repository"]
    branch = destination.get("branch", "main")
    certified_sha = destination["certified_sha"]
    token = os.getenv("GITHUB_TOKEN") or None

    try:
        if args.runtime_head_sha:
            runtime_head = args.runtime_head_sha
        else:
            head_payload = _github_json(f"https://api.github.com/repos/{repository}/commits/{branch}", token)
            runtime_head = head_payload["sha"]

        compare: dict[str, Any] | None = None
        if runtime_head != certified_sha:
            if args.compare_json:
                compare = json.loads(args.compare_json.read_text(encoding="utf-8"))
            else:
                compare = _github_json(
                    f"https://api.github.com/repos/{repository}/compare/{certified_sha}...{runtime_head}",
                    token,
                )

        report = build_report(reference, runtime_head, compare)
    except (OSError, KeyError, ValueError, urllib.error.URLError, json.JSONDecodeError) as exc:
        report = {
            "status": "AUDIT_ERROR",
            "error": str(exc),
            "promotion_required": False,
            "review_recommended": True,
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2, sort_keys=True))
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))

    if report["promotion_required"]:
        print("Runtime certification promotion review is required before the platform reference may advance.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
