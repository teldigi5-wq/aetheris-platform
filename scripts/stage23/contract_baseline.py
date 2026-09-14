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
    ROOT / "orchestrator-service" / "src" / "main" / "java",
]
HTTP = {"Get": "GET", "Post": "POST", "Put": "PUT", "Delete": "DELETE", "Patch": "PATCH"}
METHOD_MAPPING = re.compile(r"@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(([^)]*)\))?")
CLASS_MAPPING = re.compile(r"@RequestMapping\s*\(([^)]*)\)")
TABLE = re.compile(r"@Table\s*\(\s*name\s*=\s*\"([^\"]+)\"")
CLASS = re.compile(r"\bclass\s+([A-Za-z0-9_]+)")
QUOTED = re.compile(r'\"([^\"]*)\"')
AGENT_ID = re.compile(r"^\s*-\s+id:\s*([A-Za-z0-9._-]+)\s*$", re.MULTILINE)
JOB = re.compile(r"^  ([A-Za-z0-9_-]+):\s*$", re.MULTILINE)


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def first_path(args: str | None) -> str:
    if not args:
        return ""
    m = QUOTED.search(args)
    return m.group(1).strip() if m else ""


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
            verb = HTTP[match.group(1)]
            route = join_path(base, first_path(match.group(2)))
            result.append({
                "method": verb,
                "path": route,
                "controller": class_name,
                "source": path.relative_to(ROOT).as_posix(),
            })
    return sorted(result, key=lambda x: (x["path"], x["method"], x["controller"], x["source"]))


def tables(files: list[Path]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        for match in TABLE.finditer(text):
            result.append({"name": match.group(1), "source": path.relative_to(ROOT).as_posix()})
    return sorted(result, key=lambda x: (x["name"], x["source"]))


def agent_ids() -> list[str]:
    config = ROOT / "orchestrator-service" / "src" / "main" / "resources" / "application.yml"
    if not config.exists():
        return []
    return sorted(set(AGENT_ID.findall(config.read_text(encoding="utf-8"))))


def workflow_jobs() -> list[str]:
    workflow = ROOT / ".github" / "workflows" / "build.yml"
    if not workflow.exists():
        return []
    text = workflow.read_text(encoding="utf-8")
    jobs_part = text.split("\njobs:\n", 1)[1] if "\njobs:\n" in text else ""
    return sorted(set(JOB.findall(jobs_part)))


def stage_pages() -> list[str]:
    public = ROOT / "dashboard" / "public"
    return sorted(p.name for p in public.glob("stage*.html")) if public.exists() else []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/stage23/contract-baseline.json")
    args = parser.parse_args()

    files = java_files()
    payload = {
        "schemaVersion": 1,
        "httpEndpoints": endpoints(files),
        "jpaTables": tables(files),
        "agentIds": agent_ids(),
        "workflowJobs": workflow_jobs(),
        "stagePages": stage_pages(),
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
            "agentIds": len(payload["agentIds"]),
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
