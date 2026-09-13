#!/usr/bin/env python3
"""Stage 26 deterministic safety-scenario certification.

This verifier is intentionally offline and standard-library-only. It performs
source-contract certification only: it never starts services, opens sockets,
runs shell commands, invokes models/tools, or performs financial actions.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CONFIG = ROOT / "configs" / "stage26" / "safety-scenarios.json"


class CertificationError(RuntimeError):
    pass


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def safe_repo_path(relative: str) -> Path:
    if not isinstance(relative, str) or not relative.strip():
        raise CertificationError("scenario file path must be a non-empty string")
    candidate = (ROOT / relative).resolve()
    root = ROOT.resolve()
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise CertificationError(f"scenario path escapes repository root: {relative}") from exc
    if not candidate.is_file():
        raise CertificationError(f"scenario evidence file does not exist: {relative}")
    return candidate


def load_config(path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise CertificationError(f"unable to load Stage 26 config: {exc}") from exc

    if config.get("schema_version") != 1 or config.get("stage") != 26:
        raise CertificationError("Stage 26 config must declare schema_version=1 and stage=26")
    if config.get("simulation_only") is not True:
        raise CertificationError("Stage 26 must remain simulation_only=true")
    if config.get("live_side_effects") is not False:
        raise CertificationError("Stage 26 must remain live_side_effects=false")
    if config.get("physical_pc_status") != "NOT_TESTED":
        raise CertificationError("Stage 26 CI must not claim physical-PC validation")

    scenarios = config.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        raise CertificationError("Stage 26 requires at least one safety scenario")
    if len(scenarios) > 100:
        raise CertificationError("Stage 26 scenario count exceeds safety limit of 100")

    seen: set[str] = set()
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            raise CertificationError("every Stage 26 scenario must be an object")
        scenario_id = scenario.get("id")
        if not isinstance(scenario_id, str) or not scenario_id.strip():
            raise CertificationError("every Stage 26 scenario requires a non-empty id")
        if scenario_id in seen:
            raise CertificationError(f"duplicate Stage 26 scenario id: {scenario_id}")
        seen.add(scenario_id)
        for key in ("must_contain", "must_not_contain"):
            markers = scenario.get(key, [])
            if not isinstance(markers, list) or any(not isinstance(v, str) or not v for v in markers):
                raise CertificationError(f"{scenario_id}: {key} must be a list of non-empty strings")
            if any(len(v) > 1000 for v in markers):
                raise CertificationError(f"{scenario_id}: marker exceeds 1000-character limit")
    return config


def evaluate_scenario(scenario: dict[str, Any]) -> dict[str, Any]:
    relative = scenario["file"]
    path = safe_repo_path(relative)
    text = path.read_text(encoding="utf-8")

    required = list(scenario.get("must_contain", []))
    forbidden = list(scenario.get("must_not_contain", []))
    missing = [marker for marker in required if marker not in text]
    forbidden_present = [marker for marker in forbidden if marker in text]
    passed = not missing and not forbidden_present

    return {
        "id": scenario["id"],
        "category": scenario.get("category", "unspecified"),
        "description": scenario.get("description", ""),
        "status": "PASS" if passed else "FAIL",
        "evidence_file": relative,
        "evidence_sha256": sha256_file(path),
        "required_marker_count": len(required),
        "forbidden_marker_count": len(forbidden),
        "missing_markers": missing,
        "forbidden_markers_present": forbidden_present,
    }


def certify(config: dict[str, Any], *, config_path: Path = DEFAULT_CONFIG) -> dict[str, Any]:
    results = [evaluate_scenario(s) for s in config["scenarios"]]
    results.sort(key=lambda item: item["id"])
    failures = [item for item in results if item["status"] != "PASS"]
    categories = sorted({item["category"] for item in results})

    return {
        "schema_version": 1,
        "stage": 26,
        "certification": "PASS" if not failures else "FAIL",
        "simulation_only": True,
        "live_side_effects": False,
        "external_action_attempted": False,
        "physical_pc_status": "NOT_TESTED",
        "network_required": False,
        "shell_execution_required": False,
        "scenario_count": len(results),
        "pass_count": len(results) - len(failures),
        "fail_count": len(failures),
        "categories": categories,
        "config_sha256": sha256_file(config_path),
        "results": results,
    }


def write_evidence(report: dict[str, Any], output_dir: Path) -> dict[str, str]:
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = output_dir / "safety-report.json"
    report_path.write_bytes(canonical_json(report))

    evidence_files = sorted({result["evidence_file"] for result in report["results"]})
    manifest_rows = [f"{sha256_file(report_path)}  safety-report.json"]
    manifest_rows.append(f"{report['config_sha256']}  configs/stage26/safety-scenarios.json")
    for relative in evidence_files:
        manifest_rows.append(f"{sha256_file(safe_repo_path(relative))}  {relative}")

    manifest_path = output_dir / "manifest.sha256"
    manifest_path.write_text("\n".join(manifest_rows) + "\n", encoding="utf-8")
    return {
        "report": str(report_path),
        "manifest": str(manifest_path),
        "report_sha256": sha256_file(report_path),
        "manifest_sha256": sha256_file(manifest_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage 26 deterministic safety certification")
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--output-dir", type=Path, default=ROOT / "build" / "stage26")
    args = parser.parse_args()

    try:
        config = load_config(args.config)
        report = certify(config, config_path=args.config)
        evidence = write_evidence(report, args.output_dir)
    except CertificationError as exc:
        print(f"Stage 26 certification error: {exc}", file=sys.stderr)
        return 2

    print(json.dumps({
        "certification": report["certification"],
        "pass_count": report["pass_count"],
        "fail_count": report["fail_count"],
        **evidence,
    }, sort_keys=True))
    return 0 if report["certification"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
