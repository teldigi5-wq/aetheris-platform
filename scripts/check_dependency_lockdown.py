#!/usr/bin/env python3
"""Core-platform dependency-lockdown verifier.

AI-runtime dependency ownership lives in teldigi5-wq/aetheris-ai-runtime. This
platform-side verifier intentionally validates only retained core surfaces and
fails if extracted runtime source leaks back into the platform repository.
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
FORBIDDEN_LICENSE_MARKERS = ("AGPL-", "SSPL-", "BUSL-", "COMMONS CLAUSE", "GPL-3.0-ONLY")
CORE_MAVEN_MODULES = ("gateway", "user-service", "identity-service", "audit-service")
CORE_DOCKERFILES = tuple(f"{module}/Dockerfile" for module in CORE_MAVEN_MODULES)
RUNTIME_OWNED_ROOTS = ("orchestrator-service", "aetheris-quant", "aetheris-reasoning", "workstation-agent")
PINNED_IMAGE = re.compile(r"^[^@\s]+@sha256:[0-9a-f]{64}$")
MAVEN_3_9_11_SHA512 = "bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978"


def fail(message: str) -> None:
    ERRORS.append(message)


def require_file(relative: str) -> Path:
    path = ROOT / relative
    if not path.is_file():
        fail(f"missing required evidence: {relative}")
    return path


def runtime_source_boundary_errors(base: Path = ROOT) -> list[str]:
    return [
        f"runtime-owned source leaked into platform: {relative}"
        for relative in RUNTIME_OWNED_ROOTS
        if (base / relative).exists()
    ]


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


def verify_core_toolchain() -> None:
    java = require_file(".java-version")
    if java.is_file() and java.read_text(encoding="utf-8").strip() != "21.0.12":
        fail(".java-version must contain 21.0.12")
    node = require_file(".nvmrc")
    if node.is_file() and node.read_text(encoding="utf-8").strip() != "22.23.2":
        fail(".nvmrc must contain 22.23.2")
    wrapper = require_file(".mvn/wrapper/maven-wrapper.properties")
    if wrapper.is_file():
        text = wrapper.read_text(encoding="utf-8")
        if "wrapperVersion=3.3.4" not in text:
            fail("Maven wrapper must remain pinned to wrapper 3.3.4")
        if "/apache-maven/3.9.11/" not in text:
            fail("Maven wrapper distribution must remain pinned to Maven 3.9.11")


def verify_maven() -> None:
    pom_paths = ["pom.xml", *(f"{module}/pom.xml" for module in CORE_MAVEN_MODULES)]
    for relative in pom_paths:
        path = require_file(relative)
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"<version>\s*(?:LATEST|RELEASE)\s*</version>", text, re.I):
            fail(f"floating Maven version in {relative}")
        if re.search(r"<version>\s*[\[(]", text):
            fail(f"Maven version range in {relative}")
    for module in CORE_MAVEN_MODULES:
        evidence = require_file(f"build-evidence/maven/{module}.txt")
        if evidence.is_file() and evidence.stat().st_size == 0:
            fail(f"empty Maven dependency evidence for {module}")


def container_policy_errors(relative: str, text: str) -> list[str]:
    """Return deterministic supply-chain/runtime-user violations for one Dockerfile."""
    errors: list[str] = []
    lines = text.splitlines()
    from_indexes = [i for i, raw in enumerate(lines) if raw.strip().upper().startswith("FROM ")]
    if not from_indexes:
        return [f"{relative}: Dockerfile has no FROM instruction"]

    for index in from_indexes:
        parts = lines[index].strip().split()
        if len(parts) < 2 or not PINNED_IMAGE.fullmatch(parts[1]):
            image = parts[1] if len(parts) >= 2 else "<missing>"
            errors.append(f"{relative}: mutable or invalid base image {image!r}; require tag@sha256:<64-hex>")

    if "ARG MAVEN_VERSION=3.9.11" not in text:
        errors.append(f"{relative}: container build Maven must remain pinned to 3.9.11")
    if f"ARG MAVEN_SHA512={MAVEN_3_9_11_SHA512}" not in text:
        errors.append(f"{relative}: Maven 3.9.11 archive SHA-512 verification is missing or drifted")

    runtime_lines = lines[from_indexes[-1]:]
    users = [
        raw.strip().split(maxsplit=1)[1]
        for raw in runtime_lines
        if raw.strip().upper().startswith("USER ") and len(raw.strip().split(maxsplit=1)) == 2
    ]
    if not users:
        errors.append(f"{relative}: runtime stage has no explicit non-root USER")
    else:
        final_user = users[-1].strip().lower()
        principal = final_user.split(":", 1)[0]
        if principal in {"0", "root"}:
            errors.append(f"{relative}: runtime stage resolves to root USER {users[-1]!r}")

    return errors


def verify_core_containers() -> None:
    for relative in CORE_DOCKERFILES:
        path = require_file(relative)
        if not path.is_file():
            continue
        for error in container_policy_errors(relative, path.read_text(encoding="utf-8")):
            fail(error)


def verify_core_boundaries() -> None:
    for error in runtime_source_boundary_errors():
        fail(error)
    for relative in (
        "scripts/stage22/release_gate.py",
        "scripts/stage22/schema_guard.py",
        "scripts/stage22/generate_sbom.py",
        "scripts/stage22/release_manifest.py",
        "scripts/stage22/merge_readiness.py",
        "scripts/stage23/contract_baseline.py",
        "scripts/stage23/contract_guard.py",
        "scripts/stage23/expected-contract.sha256",
        "docker-compose.core.yml",
    ):
        require_file(relative)


def write_hash_manifest(scope: str) -> None:
    if scope != "core":
        raise ValueError(f"unsupported platform dependency scope: {scope}")
    evidence_files = [
        "dashboard/package.json",
        "dashboard/package-lock.json",
        "dashboard/Dockerfile",
        *CORE_DOCKERFILES,
        ".mvn/wrapper/maven-wrapper.properties",
        *(f"build-evidence/maven/{module}.txt" for module in CORE_MAVEN_MODULES),
    ]
    output = ROOT / "build-evidence/core-dependency-lock-sha256.txt"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows: list[str] = []
    for relative in evidence_files:
        path = require_file(relative)
        if path.is_file():
            rows.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {relative}")
    output.write_text("\n".join(rows) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify retained Aetheris platform-core dependency and source-ownership boundaries."
    )
    parser.add_argument(
        "--scope",
        choices=("core",),
        default="core",
        help="Platform ownership scope. AI-runtime dependencies are verified in aetheris-ai-runtime.",
    )
    parser.add_argument("--write-hashes", action="store_true")
    args = parser.parse_args()

    verify_node()
    verify_core_toolchain()
    verify_maven()
    verify_core_containers()
    verify_core_boundaries()
    if args.write_hashes:
        write_hash_manifest(args.scope)

    if ERRORS:
        print("Aetheris dependency lockdown (core) FAILED:", file=sys.stderr)
        for error in ERRORS:
            print(f" - {error}", file=sys.stderr)
        return 1
    print("Aetheris dependency lockdown (core): PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
