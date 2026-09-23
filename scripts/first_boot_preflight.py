#!/usr/bin/env python3
"""Aetheris Stage 25 platform + certified external AI-runtime readiness verifier.

CI mode validates repository-side contracts only. Host mode is reserved for the
owner's real machine and records evidence without pretending GitHub-hosted
runners are physical-PC validation. Standard library only.
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / "configs" / "first-boot-contract.json"
RUNTIME_SOURCE_ROOTS = ("orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_contract(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or data.get("stage") != 25:
        raise ValueError("first-boot contract must declare schema_version=1 and stage=25")
    return data


def result(check: str, status: str, detail: str, *, category: str) -> dict[str, str]:
    return {"check": check, "status": status, "detail": detail, "category": category}


def static_checks(contract: dict[str, Any]) -> list[dict[str, str]]:
    checks: list[dict[str, str]] = []
    for relative in contract["required_repository_files"]:
        path = ROOT / relative
        checks.append(result(f"required-file:{relative}", "PASS" if path.is_file() else "FAIL", "present" if path.is_file() else "missing", category="repository"))

    present_runtime_source = [name for name in RUNTIME_SOURCE_ROOTS if (ROOT / name).exists()]
    checks.append(result(
        "runtime-owned-source-absent",
        "PASS" if not present_runtime_source else "FAIL",
        f"present={present_runtime_source or 'none'}",
        category="ownership",
    ))

    pins = {
        ".java-version": contract["toolchain"]["java"],
        ".nvmrc": contract["toolchain"]["node"],
        ".python-version": contract["toolchain"]["python"],
    }
    for relative, expected in pins.items():
        path = ROOT / relative
        actual = path.read_text(encoding="utf-8").strip() if path.is_file() else "<missing>"
        checks.append(result(f"toolchain-pin:{relative}", "PASS" if actual == expected else "FAIL", f"expected={expected}; actual={actual}", category="toolchain"))

    wrapper = ROOT / ".mvn" / "wrapper" / "maven-wrapper.properties"
    wrapper_text = wrapper.read_text(encoding="utf-8") if wrapper.is_file() else ""
    maven_expected = contract["toolchain"]["maven"]
    checks.append(result("maven-wrapper-version", "PASS" if f"/apache-maven/{maven_expected}/" in wrapper_text else "FAIL", f"expected Maven {maven_expected}", category="toolchain"))

    compose = ROOT / contract["platform_compose_file"]
    compose_text = compose.read_text(encoding="utf-8") if compose.is_file() else ""
    discovered_ports = sorted({int(p) for p in re.findall(r'[- ]+["\']?(\d+):\d+', compose_text)})
    expected_ports = sorted(contract["expected_platform_ports"])
    missing_ports = sorted(set(expected_ports) - set(discovered_ports))
    unexpected_ports = sorted(set(discovered_ports) - set(expected_ports))
    checks.append(result("platform-compose-port-contract", "PASS" if not missing_ports and not unexpected_ports else "FAIL", f"expected={expected_ports}; discovered={discovered_ports}; missing={missing_ports}; unexpected={unexpected_ports}", category="compose"))

    compose_services = set(re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", compose_text, flags=re.MULTILINE))
    missing_services = sorted(set(contract["required_platform_services"]) - compose_services)
    checks.append(result("platform-compose-services", "PASS" if not missing_services else "FAIL", f"missing={missing_services or 'none'}", category="compose"))

    external = ROOT / contract["external_integration_compose_file"]
    external_text = external.read_text(encoding="utf-8") if external.is_file() else ""
    runtime = contract["external_ai_runtime"]
    external_markers = [
        "orchestrator-service:",
        f"image: {runtime['required_image_expression']}",
        "AETHERIS_ORCHESTRATOR_URI: ${AETHERIS_ORCHESTRATOR_URI:-http://orchestrator-service:8090}",
        '"8090:8090"',
    ]
    missing_external = [marker for marker in external_markers if marker not in external_text]
    forbidden_external = [token for token in (
        "build: ./orchestrator-service", "build: ./workstation-agent", "build: ./aetheris-quant", "build: ./aetheris-reasoning"
    ) if token in external_text]
    checks.append(result("external-runtime-compose-contract", "PASS" if not missing_external and not forbidden_external else "FAIL", f"missing={missing_external or 'none'}; forbidden={forbidden_external or 'none'}", category="external-runtime"))

    reference_path = ROOT / runtime["certification_reference"]
    reference = json.loads(reference_path.read_text(encoding="utf-8")) if reference_path.is_file() else {}
    destination = reference.get("destination_runtime", {}) if isinstance(reference, dict) else {}
    reference_ok = (
        reference.get("status") == "DESTINATION_RUNTIME_CERTIFIED"
        and destination.get("repository") == runtime["repository"]
        and destination.get("certified_sha") == runtime["certified_sha"]
        and destination.get("canonical_ci_status") == "6_OF_6_SUCCESS"
        and reference.get("source_root_deletion_status") == "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION"
        and reference.get("platform_runtime_source_present") is False
    )
    checks.append(result("external-runtime-certification-reference", "PASS" if reference_ok else "FAIL", f"repository={destination.get('repository')}; sha={destination.get('certified_sha')}; ci={destination.get('canonical_ci_status')}; deletion={reference.get('source_root_deletion_status')}", category="external-runtime"))

    forbidden_claims = contract.get("forbidden_claims_before_physical_validation", [])
    checks.append(result("physical-validation-boundary", "PASS" if forbidden_claims else "FAIL", f"{len(forbidden_claims)} pre-PC claims explicitly forbidden", category="truth-boundary"))
    return checks


def run_command(command: list[str], timeout: int = 20) -> tuple[bool, str]:
    try:
        proc = subprocess.run(command, cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
        return False, str(exc)
    output = (proc.stdout + "\n" + proc.stderr).strip()
    return proc.returncode == 0, output


def memory_gib() -> float | None:
    if os.name == "nt":
        try:
            import ctypes
            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong), ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("sullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]
            status = MEMORYSTATUSEX()
            status.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
                return round(status.ullTotalPhys / (1024**3), 2)
        except Exception:
            return None
    elif hasattr(os, "sysconf"):
        try:
            return round((os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE")) / (1024**3), 2)
        except (ValueError, OSError):
            return None
    return None


def command_version_check(name: str, command: list[str], expected_fragment: str | None) -> tuple[dict[str, str], str]:
    ok, output = run_command(command)
    if not ok:
        return result(name, "FAIL", output or "command failed", category="host-toolchain"), output
    if expected_fragment and expected_fragment not in output:
        return result(name, "FAIL", f"expected version fragment {expected_fragment!r}; output={output[:500]!r}", category="host-toolchain"), output
    return result(name, "PASS", output.splitlines()[0][:500], category="host-toolchain"), output


def host_checks(contract: dict[str, Any]) -> tuple[list[dict[str, str]], dict[str, str]]:
    checks: list[dict[str, str]] = []
    evidence: dict[str, str] = {}
    host = {"system": platform.system(), "release": platform.release(), "version": platform.version(), "machine": platform.machine(), "processor": platform.processor(), "python_runtime": platform.python_version()}
    evidence["host-inventory.json"] = json.dumps(host, indent=2, sort_keys=True) + "\n"

    total_memory = memory_gib()
    minimum_memory = float(contract["host"]["minimum_ram_gib"])
    checks.append(result("host-memory", "PASS" if total_memory is not None and total_memory >= minimum_memory else "FAIL", f"minimum={minimum_memory} GiB; detected={total_memory if total_memory is not None else 'unknown'} GiB", category="host-hardware"))
    free_disk = round(shutil.disk_usage(ROOT).free / (1024**3), 2)
    minimum_disk = float(contract["host"]["minimum_free_disk_gib"])
    checks.append(result("host-free-disk", "PASS" if free_disk >= minimum_disk else "FAIL", f"minimum={minimum_disk} GiB; detected={free_disk} GiB", category="host-hardware"))

    tool_specs = [
        ("git", ["git", "--version"], None),
        ("java", ["java", "-version"], contract["toolchain"]["java"]),
        ("maven-wrapper", [str(ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")), "-version"], contract["toolchain"]["maven"]),
        ("node", ["node", "--version"], contract["toolchain"]["node"]),
        ("python", [sys.executable, "--version"], contract["toolchain"]["python"]),
        ("docker", ["docker", "--version"], None),
        ("docker-compose", ["docker", "compose", "version"], None),
        ("docker-daemon", ["docker", "info", "--format", "{{.ServerVersion}}"], None),
    ]
    outputs: list[str] = []
    for name, command, expected in tool_specs:
        check, output = command_version_check(name, command, expected)
        checks.append(check)
        outputs.append(f"## {name}\n{output}\n")
    evidence["toolchain-versions.txt"] = "\n".join(outputs)
    evidence["docker-info.txt"] = next((x for x in outputs if x.startswith("## docker-daemon")), "")

    if os.name == "nt":
        wsl_ok, wsl_output = run_command(["wsl.exe", "--status"])
        checks.append(result("wsl-status", "PASS" if wsl_ok else "FAIL", (wsl_output or "wsl --status failed")[:1000], category="host-virtualization"))
        evidence["wsl-status.txt"] = wsl_output + "\n"
        ps_ok, ps_output = run_command(["powershell.exe", "-NoProfile", "-Command", "Get-ComputerInfo | Select-Object HyperVisorPresent | Format-List"])
        virtualization_pass = ps_ok and "HyperVisorPresent" in ps_output and "True" in ps_output
        checks.append(result("virtualization-status", "PASS" if virtualization_pass else "FAIL", (ps_output or "unable to verify HyperVisorPresent")[:1000], category="host-virtualization"))
        evidence["virtualization-status.txt"] = ps_output + "\n"
    else:
        checks.append(result("windows-host-required", "FAIL", f"contract expects Windows 11 x64; detected {platform.system()} {platform.machine()}", category="host-os"))
        evidence["wsl-status.txt"] = "Not collected: host is not Windows.\n"
        evidence["virtualization-status.txt"] = "Not collected: host is not Windows.\n"
    return checks, evidence


def summary(checks: list[dict[str, str]], mode: str, contract: dict[str, Any]) -> dict[str, Any]:
    failures = [item for item in checks if item["status"] == "FAIL"]
    return {
        "generated_at": utc_now(),
        "mode": mode,
        "status": "PASS" if not failures else "FAIL",
        "pass_count": len(checks) - len(failures),
        "fail_count": len(failures),
        "physical_pc_status": "NOT_TESTED" if mode == "ci" else "TESTED_BY_THIS_RUN",
        "foundation_scope": contract["foundation_scope"],
        "external_ai_runtime_repository": contract["external_ai_runtime"]["repository"],
        "external_ai_runtime_certified_sha": contract["external_ai_runtime"]["certified_sha"],
        "checks": checks,
    }


def write_evidence(directory: Path, report: dict[str, Any], extra: dict[str, str]) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "preflight-report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    for name, content in extra.items():
        (directory / name).write_text(content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Aetheris Stage 25 platform + external AI-runtime readiness verifier")
    parser.add_argument("--mode", choices=("ci", "host"), default="ci")
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--evidence-dir", type=Path)
    args = parser.parse_args()
    contract = load_contract(args.contract)
    checks = static_checks(contract)
    extra: dict[str, str] = {}
    if args.mode == "host":
        host_result, extra = host_checks(contract)
        checks.extend(host_result)
    report = summary(checks, args.mode, contract)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.evidence_dir:
        write_evidence(args.evidence_dir, report, extra)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
