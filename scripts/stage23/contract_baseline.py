#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOTS = [
    ROOT / "gateway" / "src" / "main" / "java",
    ROOT / "user-service" / "src" / "main" / "java",
    ROOT / "identity-service" / "src" / "main" / "java",
    ROOT / "audit-service" / "src" / "main" / "java",
]
HTTP = {"Get": "GET", "Post": "POST", "Put": "PUT", "Delete": "DELETE", "Patch": "PATCH"}
METHOD_MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(([^)]*)\))?")
CLASS_MAPPING = re.compile(r"@RequestMapping\s*\(([^)]*)\)")
TABLE = re.compile(r"@Table\s*\(\s*name\s*=\s*\"([^\"]+)\"")
CLASS = re.compile(r"\bclass\s+([A-Za-z0-9_]+)")
QUOTED = re.compile(r'\"([^\"]*)\"')
JOB = re.compile(r"^  ([A-Za-z0-9_-]+):\s*$", re.MULTILINE)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def first_path(args: str | None) -> str:
    if not args:
        return ""
    match = QUOTED.search(args)
    return match.group(1).strip() if match else ""


def join_path(base: str, leaf: str) -> str:
    parts = []
    for value in (base, leaf):
        value = (value or "").strip()
        if value and value != "/":
            parts.append(value.strip("/"))
    return "/" + "/".join(parts) if parts else "/"


def java_files() -> list[Path]:
    files: list[Path] = []
    for root in JAVA_ROOTS:
        if root.exists():
            files.extend(root.rglob("*.java"))
    return sorted(files)


def endpoints(files: list[Path]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        if "@RestController" not in text and "@Controller" not in text:
            continue
        class_match = CLASS.search(text)
        class_name = class_match.group(1) if class_match else path.stem
        prefix = text[: class_match.start()] if class_match else text[:4000]
        class_mapping = CLASS_MAPPING.findall(prefix)
        base = first_path(class_mapping[-1]) if class_mapping else ""
        for match in METHOD_MAPPING.finditer(text):
            result.append({
                "method": HTTP[match.group(1)],
                "path": join_path(base, first_path(match.group(2))),
                "controller": class_name,
                "source": path.relative_to(ROOT).as_posix(),
            })
    return sorted(result, key=lambda item: (item["path"], item["method"], item["controller"], item["source"]))


def tables(files: list[Path]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        for match in TABLE.finditer(text):
            result.append({"name": match.group(1), "source": path.relative_to(ROOT).as_posix()})
    return sorted(result, key=lambda item: (item["name"], item["source"]))


def workflow_jobs() -> list[str]:
    workflow = ROOT / ".github" / "workflows" / "build.yml"
    text = workflow.read_text(encoding="utf-8") if workflow.exists() else ""
    jobs_part = text.split("\njobs:\n", 1)[1] if "\njobs:\n" in text else ""
    return sorted(set(JOB.findall(jobs_part)))


def stage_pages() -> list[str]:
    public = ROOT / "dashboard" / "public"
    return sorted(path.name for path in public.glob("stage*.html")) if public.exists() else []


def external_runtime_boundary() -> dict[str, object]:
    reference = json.loads((ROOT / "architecture" / "ai-runtime-certification-reference.json").read_text(encoding="utf-8"))
    contract = json.loads((ROOT / "contracts" / "ai-runtime-boundary.v1.json").read_text(encoding="utf-8"))
    destination = reference["destination_runtime"]
    return {
        "status": reference["status"],
        "repository": destination["repository"],
        "revision": destination["certified_sha"],
        "canonicalCiStatus": destination["canonical_ci_status"],
        "releaseTag": destination["release_tag"],
        "imageRef": destination["image_ref"],
        "imageArchiveSha256": destination["image_archive_sha256"],
        "sourceRootDeletionStatus": reference["source_root_deletion_status"],
        "platformRuntimeSourcePresent": reference["platform_runtime_source_present"],
        "gateway": contract["gateway"],
        "migration": contract["migration"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/stage23/contract-baseline.json")
    args = parser.parse_args()
    files = java_files()
    payload = {
        "schemaVersion": 2,
        "ownershipModel": "CORE_PLATFORM_PLUS_CERTIFIED_EXTERNAL_AI_RUNTIME",
        "httpEndpoints": endpoints(files),
        "jpaTables": tables(files),
        "workflowJobs": workflow_jobs(),
        "stagePages": stage_pages(),
        "externalRuntimeBoundary": external_runtime_boundary(),
        "hardBoundaries": {
            "physicalStage21BlockedPendingHardware": True,
            "productionActivationAllowed": False,
            "liveMoneyOrdersAllowed": False,
            "withdrawalsAllowed": False,
            "transfersAllowed": False,
            "unrestrictedShellAllowed": False,
            "adminBypassAllowed": False,
        },
    }
    canonical = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    contract_sha = hashlib.sha256(canonical).hexdigest()
    result = {
        **payload,
        "contractSha256": contract_sha,
        "sourceCommit": git("rev-parse", "HEAD"),
        "counts": {
            "httpEndpoints": len(payload["httpEndpoints"]),
            "jpaTables": len(payload["jpaTables"]),
            "workflowJobs": len(payload["workflowJobs"]),
            "stagePages": len(payload["stagePages"]),
        },
    }
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"STAGE23_CONTRACT_SHA256={contract_sha}")
    print(json.dumps(result["counts"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
