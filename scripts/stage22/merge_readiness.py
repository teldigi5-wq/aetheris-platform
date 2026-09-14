#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "build" / "stage22"
OUT = BUILD / "merge-readiness.json"


def load(name: str) -> dict:
    return json.loads((BUILD / name).read_text(encoding="utf-8"))


def main() -> int:
    gate = load("release-gate.json")
    schema = load("schema-guard.json")
    sbom = load("sbom.cdx.json")
    manifest = load("release-manifest.json")

    checks = {
        "secretAndSensitiveFileGate": gate.get("status") == "PASS",
        "schemaGuard": schema.get("status") == "PASS",
        "sbomGenerated": sbom.get("bomFormat") == "CycloneDX" and bool(sbom.get("components")),
        "releaseManifestGenerated": bool(manifest.get("commitSha")) and manifest.get("trackedFileCount", 0) > 0,
        "codeOwnersPresent": (ROOT / ".github" / "CODEOWNERS").exists(),
        "securityPolicyPresent": (ROOT / "SECURITY.md").exists(),
        "dashboardLockfilePresent": (ROOT / "dashboard" / "package-lock.json").exists(),
        "mainBranchProtectionAttested": False,
    }
    weights = {
        "secretAndSensitiveFileGate": 25,
        "schemaGuard": 20,
        "sbomGenerated": 15,
        "releaseManifestGenerated": 15,
        "codeOwnersPresent": 10,
        "securityPolicyPresent": 5,
        "dashboardLockfilePresent": 5,
        "mainBranchProtectionAttested": 5,
    }
    score = sum(weights[k] for k, passed in checks.items() if passed)
    blockers = []
    if not checks["dashboardLockfilePresent"]:
        blockers.append("dashboard/package-lock.json is missing; frontend transitive dependency resolution is not fully reproducible")
    blockers.append("main branch protection and required status checks must be enabled/verified in GitHub settings before merge")

    status = "REPOSITORY_READY_FOR_OWNER_REVIEW" if score == 100 else "HARDENED_AWAITING_EXTERNAL_MERGE_GATES"
    result = {
        "schemaVersion": 1,
        "status": status,
        "score": score,
        "maxScore": 100,
        "checks": checks,
        "blockers": blockers,
        "mergeAllowed": False,
        "autoMergeAllowed": False,
        "physicalStage21Blocked": True,
        "productionActivationAllowed": False,
    }
    BUILD.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Stage 22 merge-readiness score: {score}/100 — {status}")
    for blocker in blockers:
        print(f"BLOCKER: {blocker}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
