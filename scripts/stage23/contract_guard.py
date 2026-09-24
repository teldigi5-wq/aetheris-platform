#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_RUNTIME_SHA = "6c714d1772db2db490cd035e11a78308f26f8a63"
EXPECTED_RUNTIME_ARCHIVE_SHA256 = "47e4e22cd0ff1abb8131d13194a016d01e5d6b3c20b865f554cca3d036a76855"


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default="build/stage23/contract-baseline.json")
    parser.add_argument("--expected", default="scripts/stage23/expected-contract.sha256")
    parser.add_argument("--output", default="build/stage23/contract-guard.json")
    args = parser.parse_args()

    baseline = json.loads((ROOT / args.baseline).read_text(encoding="utf-8"))
    errors: list[str] = []

    if baseline.get("schemaVersion") != 2:
        fail("Stage 23 baseline schema must be 2 after runtime extraction", errors)
    if baseline.get("ownershipModel") != "CORE_PLATFORM_PLUS_CERTIFIED_EXTERNAL_AI_RUNTIME":
        fail("Stage 23 ownership model drifted", errors)

    endpoints = baseline.get("httpEndpoints", [])
    tables = baseline.get("jpaTables", [])
    jobs = set(baseline.get("workflowJobs", []))
    pages = set(baseline.get("stagePages", []))
    boundaries = baseline.get("hardBoundaries", {})
    external = baseline.get("externalRuntimeBoundary", {})

    if len(endpoints) < 8:
        fail("core HTTP endpoint inventory unexpectedly small", errors)
    if len(tables) < 3:
        fail("core JPA table inventory unexpectedly small", errors)

    route_pairs = [(item.get("method"), item.get("path")) for item in endpoints]
    duplicates = sorted(key for key, count in Counter(route_pairs).items() if count > 1)
    if duplicates:
        fail(f"Duplicate core HTTP method/path mappings detected: {duplicates[:10]}", errors)

    table_names = [item.get("name") for item in tables]
    duplicate_tables = sorted(key for key, count in Counter(table_names).items() if count > 1)
    if duplicate_tables:
        fail(f"Duplicate explicit core JPA table names detected: {duplicate_tables[:10]}", errors)

    forbidden_api_fragments = ("/withdraw", "/transfer", "/live-order", "/live/orders")
    for item in endpoints:
        path = item.get("path", "").lower()
        if any(fragment in path for fragment in forbidden_api_fragments):
            fail(f"Forbidden financial authority route detected: {item.get('method')} {item.get('path')}", errors)

    required_jobs = {"core-backend", "dashboard", "core-source-independence", "release-hardening", "contract-freeze"}
    missing_jobs = sorted(required_jobs - jobs)
    if missing_jobs:
        fail(f"Required platform CI jobs missing: {missing_jobs}", errors)

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

    expected_external = {
        "status": "DESTINATION_RUNTIME_CERTIFIED",
        "repository": "teldigi5-wq/aetheris-ai-runtime",
        "revision": EXPECTED_RUNTIME_SHA,
        "canonicalCiStatus": "6_OF_6_SUCCESS",
        "releaseTag": f"runtime-{EXPECTED_RUNTIME_SHA}",
        "imageRef": f"aetheris-ai-runtime:{EXPECTED_RUNTIME_SHA}",
        "imageArchiveSha256": EXPECTED_RUNTIME_ARCHIVE_SHA256,
        "sourceRootDeletionStatus": "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION",
        "platformRuntimeSourcePresent": False,
    }
    for key, expected_value in expected_external.items():
        if external.get(key) != expected_value:
            fail(f"Certified external-runtime boundary drifted for {key}: {external.get(key)!r}", errors)

    gateway = external.get("gateway", {})
    if gateway.get("pathPattern") != "/api/orchestrator/**":
        fail("external runtime gateway path contract drifted", errors)
    if gateway.get("upstreamEnvironment") != "AETHERIS_ORCHESTRATOR_URI":
        fail("external runtime upstream environment contract drifted", errors)
    if gateway.get("defaultUpstream") != "http://orchestrator-service:8090":
        fail("external runtime default network identity drifted", errors)
    if gateway.get("fallbackPath") != "/fallback/orchestrator":
        fail("external runtime fallback contract drifted", errors)

    migration = external.get("migration", {})
    if migration.get("sourceExtractionComplete") is not True:
        fail("runtime source extraction completion boundary regressed", errors)
    if migration.get("externalRuntimeEndpointSupported") is not True:
        fail("external runtime endpoint support regressed", errors)
    if migration.get("externalRuntimeArtifactRequiredForSourceAbsentIntegration") is not True:
        fail("certified runtime artifact requirement regressed", errors)
    if migration.get("localComposeDefaultPreserved") is not False:
        fail("contract incorrectly claims local runtime source remains the default", errors)

    for root in ("orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent"):
        if (ROOT / root).exists():
            fail(f"runtime-owned source unexpectedly present in platform: {root}", errors)

    expected_path = ROOT / args.expected
    expected = expected_path.read_text(encoding="utf-8").strip() if expected_path.exists() else ""
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        fail("Stage 23 contract hash must be a pinned lowercase SHA-256", errors)
    elif baseline.get("contractSha256") != expected:
        fail(f"Contract hash drift: expected {expected}, observed {baseline.get('contractSha256')}", errors)

    result = {
        "schemaVersion": 2,
        "status": "PASS" if not errors else "FAIL",
        "contractSha256": baseline.get("contractSha256"),
        "baselinePinned": bool(re.fullmatch(r"[0-9a-f]{64}", expected)),
        "externalRuntimeRevision": external.get("revision"),
        "runtimeSourcePresent": any((ROOT / root).exists() for root in ("orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent")),
        "errors": errors,
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
