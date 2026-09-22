#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fail(msg: str, errors: list[str]) -> None:
    errors.append(msg)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default="build/stage23/contract-baseline.json")
    parser.add_argument("--expected", default="scripts/stage23/expected-contract.sha256")
    parser.add_argument("--output", default="build/stage23/contract-guard.json")
    args = parser.parse_args()

    baseline = json.loads((ROOT / args.baseline).read_text(encoding="utf-8"))
    errors: list[str] = []
    warnings: list[str] = []

    endpoints = baseline.get("httpEndpoints", [])
    tables = baseline.get("jpaTables", [])
    agents = baseline.get("agentIds", [])
    jobs = set(baseline.get("workflowJobs", []))
    pages = set(baseline.get("stagePages", []))
    boundaries = baseline.get("hardBoundaries", {})

    if len(endpoints) < 20:
        fail("HTTP endpoint inventory unexpectedly small", errors)
    if len(tables) < 10:
        fail("JPA table inventory unexpectedly small", errors)
    if len(agents) < 20:
        fail("Agent catalog unexpectedly small", errors)

    route_pairs = [(x.get("method"), x.get("path")) for x in endpoints]
    duplicates = sorted(k for k, n in Counter(route_pairs).items() if n > 1)
    if duplicates:
        fail(f"Duplicate HTTP method/path mappings detected: {duplicates[:10]}", errors)

    table_names = [x.get("name") for x in tables]
    duplicate_tables = sorted(k for k, n in Counter(table_names).items() if n > 1)
    if duplicate_tables:
        fail(f"Duplicate explicit JPA table names detected: {duplicate_tables[:10]}", errors)

    required_stage_prefixes = [f"/api/orchestrator/stage{n}" for n in range(18, 22)]
    paths = [x.get("path", "") for x in endpoints]
    for prefix in required_stage_prefixes:
        if not any(p.startswith(prefix) for p in paths):
            fail(f"Required API contract missing: {prefix}", errors)

    forbidden_api_fragments = ("/withdraw", "/transfer", "/live-order", "/live/orders")
    for item in endpoints:
        path = item.get("path", "").lower()
        if any(fragment in path for fragment in forbidden_api_fragments):
            fail(f"Forbidden financial authority route detected: {item.get('method')} {item.get('path')}", errors)

    required_jobs = {
        "core-backend",
        "dashboard",
        "core-source-independence",
        "release-hardening",
        "contract-freeze",
    }
    missing_jobs = sorted(required_jobs - jobs)
    if missing_jobs:
        fail(f"Required CI jobs missing: {missing_jobs}", errors)

    for page in ("stage21.html", "stage22.html", "stage23.html"):
        if page not in pages:
            fail(f"Required evidence console missing: {page}", errors)

    expected_boundaries = {
        "physicalStage21BlockedPendingHardware": True,
        "productionActivationAllowed": False,
        "liveMoneyOrdersAllowed": False,
        "withdrawalsAllowed": False,
        "transfersAllowed": False,
        "unrestrictedShellAllowed": False,
        "adminBypassAllowed": False,
    }
    if boundaries != expected_boundaries:
        fail("Stage 23 hard-boundary payload drifted", errors)

    env_example = (ROOT / "aetheris-quant" / ".env.example").read_text(encoding="utf-8")
    if not re.search(r"(?m)^ENABLE_TESTNET_EXECUTION=false\s*$", env_example):
        fail("aetheris-quant testnet execution default is no longer false", errors)

    stage22 = (ROOT / "scripts" / "stage22" / "merge_readiness.py").read_text(encoding="utf-8")
    if '"mergeAllowed": False' not in stage22 or '"autoMergeAllowed": False' not in stage22:
        fail("Stage 22 merge authority boundary changed", errors)

    stage21_dir = ROOT / "orchestrator-service" / "src" / "main" / "java" / "io" / "aetheris" / "orchestrator" / "stage21"
    stage21_text = "\n".join(p.read_text(encoding="utf-8") for p in sorted(stage21_dir.glob("*.java")))
    for token in ("physicalPilotComplete", "ownerPilotActivationAllowed", "productionActivationAllowed"):
        if token not in stage21_text:
            fail(f"Stage 21 hardware boundary token missing: {token}", errors)

    expected_path = ROOT / args.expected
    expected = expected_path.read_text(encoding="utf-8").strip() if expected_path.exists() else ""
    pinned = bool(re.fullmatch(r"[0-9a-f]{64}", expected))
    if pinned:
        if baseline.get("contractSha256") != expected:
            fail(
                f"Contract hash drift: expected {expected}, observed {baseline.get('contractSha256')}",
                errors,
            )
    else:
        warnings.append("Contract hash is not pinned yet; Stage 23 is in bootstrap mode")

    result = {
        "schemaVersion": 1,
        "status": "PASS" if not errors else "FAIL",
        "contractSha256": baseline.get("contractSha256"),
        "baselinePinned": pinned,
        "errors": errors,
        "warnings": warnings,
        "productionActivationAllowed": False,
        "liveMoneyOrdersAllowed": False,
        "physicalStage21BlockedPendingHardware": True,
    }
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
