#!/usr/bin/env python3
"""Verify the final Step D source-extracted platform boundary."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "architecture" / "ai-runtime-extraction-manifest.json"
REFERENCE_PATH = ROOT / "architecture" / "ai-runtime-certification-reference.json"
CONTRACT_PATH = ROOT / "contracts" / "ai-runtime-boundary.v1.json"
EVIDENCE_DIR = ROOT / "build-evidence" / "architecture"
EXPECTED_CORE_MODULES = {"gateway", "user-service", "identity-service", "audit-service"}
EXPECTED_DESTINATION = "teldigi5-wq/aetheris-ai-runtime"
EXPECTED_DESTINATION_SHA = "e2af887c13c6851ce950f04d093d190fe791f8fa"
EXPECTED_ARCHIVE_SHA256 = "790a2c4fb8bd3d651b66f57365cea24ae10e6183a423278c02a1ad791bbe5ade"


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate_root_pom(errors: list[str]) -> list[str]:
    pom = ROOT / "pom.xml"
    if not pom.is_file():
        errors.append("missing root pom.xml")
        return []
    try:
        tree = ET.parse(pom)
    except ET.ParseError as exc:
        errors.append(f"root pom.xml is not valid XML: {exc}")
        return []
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [
        node.text.strip()
        for node in tree.findall("m:modules/m:module", namespace)
        if node.text and node.text.strip()
    ]
    if set(modules) != EXPECTED_CORE_MODULES or len(modules) != len(EXPECTED_CORE_MODULES):
        errors.append(
            f"root Maven reactor must contain only core modules {sorted(EXPECTED_CORE_MODULES)}, got {modules}"
        )
    return modules


def require_file(relative: str, errors: list[str]) -> None:
    if not (ROOT / relative).is_file():
        errors.append(f"missing platform-retained asset: {relative}")


def main() -> int:
    errors: list[str] = []
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    reference = json.loads(REFERENCE_PATH.read_text(encoding="utf-8"))
    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))

    if manifest.get("schema_version") != 2:
        errors.append("manifest schema_version must be 2 after final extraction")
    if manifest.get("status") != "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION":
        errors.append("manifest must declare SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION")
    if manifest.get("destination_repository") != EXPECTED_DESTINATION:
        errors.append("destination repository drifted")
    if manifest.get("destination_certified_sha") != EXPECTED_DESTINATION_SHA:
        errors.append("destination certified SHA drifted")
    if manifest.get("destination_ci_status") != "6_OF_6_SUCCESS":
        errors.append("destination canonical CI certification drifted")

    move_roots = list(manifest.get("move_roots", []))
    expected_roots = [
        "orchestrator-service",
        "aetheris-quant",
        "aetheris-reasoning",
        "workstation-agent",
    ]
    if move_roots != expected_roots:
        errors.append(f"move_roots drifted: {move_roots}")
    present_roots = [root for root in expected_roots if (ROOT / root).exists()]
    if present_roots:
        errors.append(f"runtime-owned source roots still present in platform: {present_roots}")

    removed_workflows = list(manifest.get("runtime_owned_workflows_removed_from_platform", []))
    if len(removed_workflows) != 33 or len(set(removed_workflows)) != 33:
        errors.append(f"expected exactly 33 unique runtime-owned workflows, got {len(removed_workflows)}")
    leaked_workflows = [path for path in removed_workflows if (ROOT / path).exists()]
    if leaked_workflows:
        errors.append(f"runtime-owned workflows still present in platform: {leaked_workflows}")

    for relative in manifest.get("platform_retained_contract_assets", []):
        require_file(relative, errors)
    for relative in manifest.get("platform_retained_certifications", []):
        require_file(relative, errors)

    modules = validate_root_pom(errors)

    runtime = reference.get("destination_runtime", {})
    if reference.get("status") != "DESTINATION_RUNTIME_CERTIFIED":
        errors.append("runtime certification reference is not certified")
    if reference.get("source_root_deletion_status") != "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION":
        errors.append("runtime certification reference does not permit final source absence")
    if reference.get("platform_runtime_source_present") is not False:
        errors.append("runtime certification reference claims platform runtime source is present")
    if runtime.get("repository") != EXPECTED_DESTINATION:
        errors.append("runtime reference repository drifted")
    if runtime.get("certified_sha") != EXPECTED_DESTINATION_SHA:
        errors.append("runtime reference SHA drifted")
    if runtime.get("canonical_ci_status") != "6_OF_6_SUCCESS":
        errors.append("runtime reference canonical CI status drifted")
    if runtime.get("image_archive_sha256") != EXPECTED_ARCHIVE_SHA256:
        errors.append("runtime release archive digest drifted")

    release = manifest.get("destination_runtime_release", {})
    if release.get("archive_sha256") != EXPECTED_ARCHIVE_SHA256:
        errors.append("manifest release archive digest drifted")
    if release.get("image_ref") != runtime.get("image_ref"):
        errors.append("manifest/reference image refs diverged")
    if release.get("tag") != runtime.get("release_tag"):
        errors.append("manifest/reference release tags diverged")

    source_boundary = contract.get("sourceBoundary", {})
    migration = contract.get("migration", {})
    certified_runtime = contract.get("certifiedRuntime", {})
    if source_boundary.get("platformContainsAiRuntimeSource") is not False:
        errors.append("v1 boundary contract does not declare platform source absence")
    if migration.get("sourceExtractionComplete") is not True:
        errors.append("v1 boundary contract does not declare extraction complete")
    if migration.get("localComposeDefaultPreserved") is not False:
        errors.append("v1 boundary contract still claims local runtime source default")
    if certified_runtime.get("repository") != EXPECTED_DESTINATION:
        errors.append("v1 boundary contract runtime repository drifted")
    if certified_runtime.get("revision") != EXPECTED_DESTINATION_SHA:
        errors.append("v1 boundary contract runtime revision drifted")

    default_compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
    external_compose = (ROOT / "docker-compose.integration-external.yml").read_text(encoding="utf-8")
    forbidden_local_builds = (
        "build: ./orchestrator-service",
        "build: ./workstation-agent",
        "build: ./aetheris-quant",
        "build: ./aetheris-reasoning",
    )
    for token in forbidden_local_builds:
        if token in default_compose or token in external_compose:
            errors.append(f"local runtime source build reference remains: {token}")
    expected_remote_context = (
        "https://github.com/teldigi5-wq/aetheris-ai-runtime.git#"
        f"{EXPECTED_DESTINATION_SHA}:orchestrator-service"
    )
    if expected_remote_context not in default_compose:
        errors.append("default Compose does not pin the exact destination runtime build context")
    if "build:" in external_compose.split("  orchestrator-service:", 1)[1].split("\n  gateway:", 1)[0]:
        errors.append("external integration orchestrator must remain image-only")

    prerequisites = manifest.get("deletion_prerequisites_satisfied", {})
    false_prerequisites = sorted(key for key, value in prerequisites.items() if value is not True)
    if false_prerequisites:
        errors.append(f"deletion prerequisite(s) are not satisfied: {false_prerequisites}")

    revision = git("rev-parse", "HEAD")
    report = {
        "schema_version": 2,
        "status": "PASS" if not errors else "FAIL",
        "mode": "platform-source-extracted",
        "platform_revision": revision,
        "destination_repository": EXPECTED_DESTINATION,
        "destination_certified_sha": EXPECTED_DESTINATION_SHA,
        "runtime_release_archive_sha256": EXPECTED_ARCHIVE_SHA256,
        "move_roots": move_roots,
        "runtime_owned_workflows_removed": removed_workflows,
        "root_maven_modules": modules,
        "platform_runtime_source_present": bool(present_roots),
        "manifest_sha256": sha256_file(MANIFEST_PATH),
        "certification_reference_sha256": sha256_file(REFERENCE_PATH),
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "truth_boundaries": manifest.get("truth_boundaries", {}),
        "errors": errors,
    }
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    (EVIDENCE_DIR / "ai-runtime-extraction-final-report.json").write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    if errors:
        print("Final AI-runtime extraction verification FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print(
        "Final AI-runtime extraction verification: PASS "
        f"(4 source roots absent, {len(removed_workflows)} runtime workflows removed, "
        f"destination={EXPECTED_DESTINATION}@{EXPECTED_DESTINATION_SHA})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
