#!/usr/bin/env python3
"""Stage 27 canonical branch governance and release-promotion guard.

The verifier is intentionally offline and deterministic. It does not call GitHub,
mutate refs, or claim branch protection that is not configured. It validates the
repository policy, workflow trigger hygiene, README truth markers, and the current
CI event context supplied by GitHub Actions.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = ROOT / "configs" / "stage27" / "repository-governance.json"


class GovernanceError(RuntimeError):
    pass


def load_config(path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise GovernanceError(f"unable to load Stage 27 config: {exc}") from exc

    if config.get("schema_version") != 1 or config.get("stage") != 27:
        raise GovernanceError("Stage 27 config must declare schema_version=1 and stage=27")

    stable = config.get("stable_branch")
    canonical = config.get("canonical_development_branch")
    if stable != "main":
        raise GovernanceError("Stage 27 stable branch must remain main")
    if canonical != "feature/syntra-aetheris-foundation-v2":
        raise GovernanceError("Stage 27 canonical development branch changed unexpectedly")
    if stable == canonical:
        raise GovernanceError("stable and canonical development branches must be different")

    if config.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        raise GovernanceError("Stage 27 must not claim physical-PC validation")
    if config.get("release_to_main_requires_review") is not True:
        raise GovernanceError("release_to_main_requires_review must remain true")
    if config.get("direct_stage_branch_development_allowed") is not False:
        raise GovernanceError("direct_stage_branch_development_allowed must remain false")

    legacy = config.get("legacy_branches")
    if not isinstance(legacy, list) or not legacy:
        raise GovernanceError("legacy_branches must be a non-empty list")
    if any(not isinstance(item, str) or not item.strip() for item in legacy):
        raise GovernanceError("legacy branch names must be non-empty strings")
    if len(set(legacy)) != len(legacy):
        raise GovernanceError("legacy branch names must be unique")
    if stable in legacy or canonical in legacy:
        raise GovernanceError("stable/canonical branch cannot be marked legacy")

    allowed_heads = config.get("allowed_main_pr_heads")
    if allowed_heads != [canonical]:
        raise GovernanceError("canonical development branch must remain an allowed main PR head")

    allowed_prefixes = config.get("allowed_main_pr_head_prefixes")
    if allowed_prefixes != ["release/"]:
        raise GovernanceError("main release branch prefix policy changed unexpectedly")

    return config


def workflow_files(root: Path = ROOT) -> list[Path]:
    directory = root / ".github" / "workflows"
    if not directory.is_dir():
        raise GovernanceError(".github/workflows directory is missing")
    return sorted([*directory.glob("*.yml"), *directory.glob("*.yaml")])


def scan_legacy_workflow_references(config: dict[str, Any], root: Path = ROOT) -> list[dict[str, str]]:
    hits: list[dict[str, str]] = []
    legacy = config["legacy_branches"]
    for path in workflow_files(root):
        text = path.read_text(encoding="utf-8")
        for branch in legacy:
            if branch in text:
                hits.append(
                    {
                        "workflow": path.relative_to(root).as_posix(),
                        "legacy_branch": branch,
                    }
                )
    return hits


def readme_errors(config: dict[str, Any], root: Path = ROOT) -> list[str]:
    path = root / "README.md"
    if not path.is_file():
        return ["README.md is missing"]
    text = path.read_text(encoding="utf-8")
    return [
        f"README.md missing required marker: {marker}"
        for marker in config.get("required_readme_markers", [])
        if marker not in text
    ]


def is_allowed_main_pr_head(config: dict[str, Any], head_ref: str) -> bool:
    if head_ref in config["allowed_main_pr_heads"]:
        return True
    return any(
        head_ref.startswith(prefix) and len(head_ref) > len(prefix)
        for prefix in config["allowed_main_pr_head_prefixes"]
    )


def evaluate_event_context(
    config: dict[str, Any],
    *,
    event_name: str,
    ref_name: str,
    head_ref: str,
    base_ref: str,
) -> tuple[bool, list[str]]:
    errors: list[str] = []
    canonical = config["canonical_development_branch"]
    stable = config["stable_branch"]

    if event_name == "push":
        if ref_name != canonical:
            errors.append(
                f"Stage 27 push validation is intended for canonical branch {canonical}; got {ref_name or '<empty>'}"
            )
    elif event_name == "pull_request":
        if base_ref == stable and not is_allowed_main_pr_head(config, head_ref):
            errors.append(
                f"pull requests to {stable} must originate from {canonical} or a reviewed release/* branch; "
                f"got {head_ref or '<empty>'}"
            )
        if head_ref in config["legacy_branches"]:
            errors.append(f"legacy branch {head_ref} cannot be used for active development or release promotion")
    elif event_name in {"workflow_dispatch", "schedule", ""}:
        pass
    else:
        errors.append(f"unsupported Stage 27 event context: {event_name}")

    return (not errors, errors)


def build_report(
    config: dict[str, Any],
    *,
    event_name: str,
    ref_name: str,
    head_ref: str,
    base_ref: str,
    root: Path = ROOT,
) -> dict[str, Any]:
    errors: list[str] = []

    legacy_hits = scan_legacy_workflow_references(config, root)
    if legacy_hits:
        errors.extend(
            f"workflow {hit['workflow']} still references legacy branch {hit['legacy_branch']}"
            for hit in legacy_hits
        )

    errors.extend(readme_errors(config, root))
    promotion_allowed, context_errors = evaluate_event_context(
        config,
        event_name=event_name,
        ref_name=ref_name,
        head_ref=head_ref,
        base_ref=base_ref,
    )
    errors.extend(context_errors)

    return {
        "schemaVersion": 1,
        "stage": 27,
        "status": "PASS" if not errors else "FAIL",
        "stableBranch": config["stable_branch"],
        "canonicalDevelopmentBranch": config["canonical_development_branch"],
        "physicalPcStatus": config["physical_pc_status"],
        "releaseToMainRequiresReview": config["release_to_main_requires_review"],
        "directStageBranchDevelopmentAllowed": config["direct_stage_branch_development_allowed"],
        "event": {
            "name": event_name,
            "refName": ref_name,
            "headRef": head_ref,
            "baseRef": base_ref,
        },
        "promotionAllowedByContext": promotion_allowed,
        "legacyWorkflowReferences": legacy_hits,
        "errors": errors,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage 27 repository governance verifier")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "stage27" / "governance-report.json")
    parser.add_argument("--event-name", default=os.getenv("GITHUB_EVENT_NAME", ""))
    parser.add_argument("--ref-name", default=os.getenv("GITHUB_REF_NAME", ""))
    parser.add_argument("--head-ref", default=os.getenv("GITHUB_HEAD_REF", ""))
    parser.add_argument("--base-ref", default=os.getenv("GITHUB_BASE_REF", ""))
    args = parser.parse_args()

    try:
        config = load_config(args.config)
        report = build_report(
            config,
            event_name=args.event_name,
            ref_name=args.ref_name,
            head_ref=args.head_ref,
            base_ref=args.base_ref,
        )
    except GovernanceError as exc:
        print(f"Stage 27 governance error: {exc}", file=sys.stderr)
        return 2

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, sort_keys=True))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
