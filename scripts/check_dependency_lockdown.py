#!/usr/bin/env python3
"""Stage 24/25 dependency-lockdown verifier for the full Aetheris foundation.

Uses only the Python standard library so the verifier itself adds no package drift.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

EXACT_SEMVER = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
FORBIDDEN_LICENSE_MARKERS = (
    "AGPL-",
    "SSPL-",
    "BUSL-",
    "COMMONS CLAUSE",
    "GPL-3.0-ONLY",
)

MAVEN_MODULES = (
    "gateway",
    "user-service",
    "identity-service",
    "audit-service",
    "orchestrator-service",
    "workstation-agent",
)


def fail(message: str) -> None:
    ERRORS.append(message)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"missing required evidence: {relative}")
    return path


def verify_node() -> None:
    package_path = require_file("dashboard/package.json")
    lock_path = require_file("dashboard/package-lock.json")
    if not package_path.is_file() or not lock_path.is_file():
        return

    package = json.loads(package_path.read_text(encoding="utf-8"))
    lock = json.loads(lock_path.read_text(encoding="utf-8"))

    for section in ("dependencies", "devDependencies"):
        declared = package.get(section, {})
        for name, version in declared.items():
            if not EXACT_SEMVER.fullmatch(version):
                fail(f"dashboard {name} must be exact semver, got {version!r}")

    if package.get("engines", {}).get("node") != "22.23.2":
        fail("dashboard Node engine must remain pinned to 22.23.2")

    if lock.get("lockfileVersion") != 3:
        fail("dashboard package-lock.json must use lockfileVersion 3")

    root_lock = lock.get("packages", {}).get("", {})
    for section in ("dependencies", "devDependencies"):
        if root_lock.get(section, {}) != package.get(section, {}):
            fail(f"dashboard package.json and package-lock.json disagree in {section}")

    integrity_count = 0
    for name, metadata in lock.get("packages", {}).items():
        if not name:
            continue
        if metadata.get("integrity"):
            integrity_count += 1
        license_value = str(metadata.get("license", "")).upper()
        for marker in FORBIDDEN_LICENSE_MARKERS:
            if marker in license_value:
                fail(f"forbidden npm license marker {marker!r} in {name}")

    if integrity_count == 0:
        fail("dashboard lockfile contains no package integrity hashes")


def verify_python() -> None:
    source_path = require_file("aetheris-quant/requirements.in")
    lock_path = require_file("aetheris-quant/requirements.lock.txt")
    if not source_path.is_file() or not lock_path.is_file():
        return

    for raw in source_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "==" not in line or any(token in line for token in (">=", "<=", "~=", "!=", "*")):
            fail(f"Python direct dependency must be exactly pinned: {line}")

    lines = lock_path.read_text(encoding="utf-8").splitlines()
    starts = [
        i
        for i, line in enumerate(lines)
        if line and not line[0].isspace() and not line.startswith("#") and "==" in line
    ]
    if not starts:
        fail("Python lockfile contains no pinned requirements")
        return

    for position, start in enumerate(starts):
        end = starts[position + 1] if position + 1 < len(starts) else len(lines)
        block = "\n".join(lines[start:end])
        requirement = lines[start].split("\\", 1)[0].strip()
        if "--hash=sha256:" not in block:
            fail(f"Python lock entry has no SHA-256 hash: {requirement}")


def verify_toolchains() -> None:
    expected = {
        ".nvmrc": "22.23.2",
        ".java-version": "21.0.12",
        "aetheris-quant/.python-version": "3.13.15",
    }
    for relative, wanted in expected.items():
        path = require_file(relative)
        if path.is_file() and path.read_text(encoding="utf-8").strip() != wanted:
            fail(f"{relative} must contain {wanted}")

    wrapper = require_file(".mvn/wrapper/maven-wrapper.properties")
    if wrapper.is_file():
        text = wrapper.read_text(encoding="utf-8")
        if "wrapperVersion=3.3.4" not in text:
            fail("Maven wrapper must remain pinned to wrapper 3.3.4")
        if "/apache-maven/3.9.11/" not in text:
            fail("Maven wrapper distribution must remain pinned to Maven 3.9.11")


def verify_maven() -> None:
    pom_paths = ["pom.xml", *(f"{module}/pom.xml" for module in MAVEN_MODULES)]
    for relative in pom_paths:
        path = require_file(relative)
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"<version>\s*(?:LATEST|RELEASE)\s*</version>", text, re.I):
            fail(f"floating Maven version in {relative}")
        if re.search(r"<version>\s*[\[(]", text):
            fail(f"Maven version range in {relative}")

    for module in MAVEN_MODULES:
        evidence = require_file(f"build-evidence/maven/{module}.txt")
        if evidence.is_file() and evidence.stat().st_size == 0:
            fail(f"empty Maven dependency evidence for {module}")


def verify_foundation_boundaries() -> None:
    required = (
        "scripts/stage22/release_gate.py",
        "scripts/stage22/schema_guard.py",
        "scripts/stage22/generate_sbom.py",
        "scripts/stage22/release_manifest.py",
        "scripts/stage22/merge_readiness.py",
        "scripts/stage23/contract_baseline.py",
        "scripts/stage23/contract_guard.py",
        "scripts/stage23/expected-contract.sha256",
        "orchestrator-service/pom.xml",
        "workstation-agent/pom.xml",
    )
    for relative in required:
        require_file(relative)


def write_hash_manifest() -> None:
    evidence_files = [
        "dashboard/package.json",
        "dashboard/package-lock.json",
        "dashboard/Dockerfile",
        "aetheris-quant/requirements.in",
        "aetheris-quant/requirements.lock.txt",
        ".mvn/wrapper/maven-wrapper.properties",
        *(f"build-evidence/maven/{module}.txt" for module in MAVEN_MODULES),
    ]
    output = ROOT / "build-evidence/dependency-lock-sha256.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows: list[str] = []
    for relative in evidence_files:
        path = require_file(relative)
        if path.is_file():
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            rows.append(f"{digest}  {relative}")
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-hashes", action="store_true")
    args = parser.parse_args()

    verify_node()
    verify_python()
    verify_toolchains()
    verify_maven()
    verify_foundation_boundaries()
    if args.write_hashes:
        write_hash_manifest()

    if ERRORS:
        print("Aetheris dependency lockdown FAILED:", file=sys.stderr)
        for error in ERRORS:
            print(f" - {error}", file=sys.stderr)
        return 1

    print("Aetheris dependency lockdown: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
