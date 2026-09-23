#!/usr/bin/env python3
"""Load and verify the certified Aetheris AI runtime release artifact.

This loader consumes the exact-revision image archive published by the separate
`teldigi5-wq/aetheris-ai-runtime` repository. It verifies the archive digest,
Docker image identity and OCI provenance labels before exposing the image to
platform integration workflows.

The release is a hosted CI artifact. Loading it does not claim registry
publication, production deployment, or physical owner-PC validation.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import subprocess
import tempfile
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONTRACT = ROOT / "architecture" / "ai-runtime-consumption.json"
DEFAULT_REPORT = ROOT / "build-evidence" / "runtime" / "ai-runtime-consumption-report.json"


def run(args: list[str], *, stdin=None, timeout: int = 300) -> str:
    result = subprocess.run(
        args,
        cwd=ROOT,
        stdin=stdin,
        text=stdin is None,
        capture_output=True,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        stderr = result.stderr.decode("utf-8", errors="replace") if isinstance(result.stderr, bytes) else result.stderr
        stdout = result.stdout.decode("utf-8", errors="replace") if isinstance(result.stdout, bytes) else result.stdout
        raise RuntimeError(
            f"command failed ({result.returncode}): {' '.join(args)}\n"
            f"stdout={str(stdout)[-4000:]}\nstderr={str(stderr)[-4000:]}"
        )
    stdout = result.stdout.decode("utf-8", errors="replace") if isinstance(result.stdout, bytes) else result.stdout
    return str(stdout).strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_string(contract: dict[str, Any], key: str) -> str:
    value = contract.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"invalid or missing contract field: {key}")
    return value.strip()


def validate_contract(contract: dict[str, Any]) -> None:
    if contract.get("schema_version") != 1:
        raise ValueError("unsupported AI runtime consumption contract schema")
    if contract.get("status") != "CERTIFIED_DESTINATION_RUNTIME_PINNED":
        raise ValueError("AI runtime consumption contract is not certified/pinned")

    repository = require_string(contract, "runtime_repository")
    revision = require_string(contract, "runtime_revision")
    release_tag = require_string(contract, "release_tag")
    archive_name = require_string(contract, "archive_name")
    archive_sha256 = require_string(contract, "archive_sha256")
    image_tag = require_string(contract, "image_tag")
    image_id = require_string(contract, "image_id")
    image_source = require_string(contract, "image_source")
    evidence_class = require_string(contract, "image_evidence_class")

    if len(revision) != 40 or any(ch not in "0123456789abcdef" for ch in revision):
        raise ValueError(f"runtime revision is not a full lowercase Git SHA: {revision!r}")
    if release_tag != f"runtime-{revision}":
        raise ValueError("release tag is not bound to the runtime revision")
    if revision not in archive_name or not archive_name.endswith(".tar.gz"):
        raise ValueError("archive name is not exact-revision bound")
    if len(archive_sha256) != 64 or any(ch not in "0123456789abcdef" for ch in archive_sha256):
        raise ValueError("archive SHA-256 is malformed")
    if image_tag.endswith(":latest") or revision not in image_tag:
        raise ValueError("runtime image tag must be exact-revision pinned and never latest")
    if not image_id.startswith("sha256:") or len(image_id) != 71:
        raise ValueError("runtime image ID is malformed")
    if image_source != f"https://github.com/{repository}":
        raise ValueError("runtime image source does not match the destination repository")
    if evidence_class != "HOSTED_RUNTIME_ARTIFACT":
        raise ValueError("unsupported runtime evidence class")

    boundaries = contract.get("truth_boundaries")
    if not isinstance(boundaries, dict):
        raise ValueError("missing truth boundaries")
    if boundaries.get("physical_pc_validation") is not False:
        raise ValueError("physical-PC validation must remain false")
    if boundaries.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        raise ValueError("physical-PC status must remain BLOCKED_PENDING_HARDWARE")
    if boundaries.get("production_deployment_claim") is not False:
        raise ValueError("production deployment claim must remain false")
    if boundaries.get("registry_publication_claim") is not False:
        raise ValueError("registry publication claim must remain false")


def release_url(contract: dict[str, Any]) -> str:
    return (
        f"https://github.com/{require_string(contract, 'runtime_repository')}/releases/download/"
        f"{require_string(contract, 'release_tag')}/{require_string(contract, 'archive_name')}"
    )


def download(url: str, destination: Path) -> None:
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "aetheris-platform-external-runtime-loader/1"},
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=90) as response, destination.open("wb") as output:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            output.write(chunk)


def docker_load(archive: Path) -> str:
    with gzip.open(archive, "rb") as stream:
        process = subprocess.run(
            ["docker", "load"],
            cwd=ROOT,
            stdin=stream,
            capture_output=True,
            timeout=300,
            check=False,
        )
    if process.returncode != 0:
        raise RuntimeError(
            "docker load failed: "
            + process.stderr.decode("utf-8", errors="replace")[-4000:]
        )
    return process.stdout.decode("utf-8", errors="replace").strip()


def write_github_env(path: Path, contract: dict[str, Any]) -> None:
    values = {
        "AETHERIS_AI_RUNTIME_IMAGE": require_string(contract, "image_tag"),
        "AETHERIS_AI_RUNTIME_REVISION": require_string(contract, "runtime_revision"),
        "AETHERIS_AI_RUNTIME_IMAGE_ID": require_string(contract, "image_id"),
        "AETHERIS_AI_RUNTIME_ARCHIVE_SHA256": require_string(contract, "archive_sha256"),
        "AETHERIS_EXTERNAL_ORCHESTRATOR_IMAGE": require_string(contract, "image_tag"),
    }
    with path.open("a", encoding="utf-8") as handle:
        for name, value in values.items():
            if "\n" in value or "\r" in value:
                raise ValueError(f"unsafe environment value for {name}")
            handle.write(f"{name}={value}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Load the certified external Aetheris AI runtime image")
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    parser.add_argument("--output", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--github-env", type=Path, default=None)
    args = parser.parse_args()

    contract = json.loads(args.contract.read_text(encoding="utf-8"))
    validate_contract(contract)

    image_tag = require_string(contract, "image_tag")
    expected_image_id = require_string(contract, "image_id")
    expected_revision = require_string(contract, "runtime_revision")
    expected_archive_sha256 = require_string(contract, "archive_sha256")
    expected_source = require_string(contract, "image_source")
    expected_evidence_class = require_string(contract, "image_evidence_class")
    url = release_url(contract)

    with tempfile.TemporaryDirectory(prefix="aetheris-ai-runtime-") as temp_dir:
        archive = Path(temp_dir) / require_string(contract, "archive_name")
        download(url, archive)
        actual_archive_sha256 = sha256_file(archive)
        if actual_archive_sha256 != expected_archive_sha256:
            raise RuntimeError(
                f"runtime archive digest mismatch: expected={expected_archive_sha256} actual={actual_archive_sha256}"
            )
        load_output = docker_load(archive)

    actual_image_id = run(["docker", "image", "inspect", image_tag, "--format", "{{.Id}}"])
    if actual_image_id != expected_image_id:
        raise RuntimeError(
            f"runtime image identity mismatch: expected={expected_image_id} actual={actual_image_id}"
        )

    revision_label = run(
        ["docker", "image", "inspect", image_tag, "--format", "{{index .Config.Labels \"org.opencontainers.image.revision\"}}"]
    )
    source_label = run(
        ["docker", "image", "inspect", image_tag, "--format", "{{index .Config.Labels \"org.opencontainers.image.source\"}}"]
    )
    evidence_label = run(
        ["docker", "image", "inspect", image_tag, "--format", "{{index .Config.Labels \"io.aetheris.evidence-class\"}}"]
    )
    if revision_label != expected_revision:
        raise RuntimeError(f"runtime revision label mismatch: {revision_label!r}")
    if source_label != expected_source:
        raise RuntimeError(f"runtime source label mismatch: {source_label!r}")
    if evidence_label != expected_evidence_class:
        raise RuntimeError(f"runtime evidence label mismatch: {evidence_label!r}")

    github_env = args.github_env
    if github_env is None and os.environ.get("GITHUB_ENV"):
        github_env = Path(os.environ["GITHUB_ENV"])
    if github_env is not None:
        write_github_env(github_env, contract)

    report = {
        "schema_version": 1,
        "status": "PASS",
        "evidence_class": "HOSTED_RUNTIME_ARTIFACT_CONSUMPTION",
        "runtime_repository": contract["runtime_repository"],
        "runtime_revision": expected_revision,
        "release_tag": contract["release_tag"],
        "release_url": url,
        "archive_sha256": expected_archive_sha256,
        "image_tag": image_tag,
        "image_id": actual_image_id,
        "oci_revision": revision_label,
        "oci_source": source_label,
        "oci_evidence_class": evidence_label,
        "docker_load_output": load_output,
        "physical_pc_validation": False,
        "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
        "production_deployment_claim": False,
        "registry_publication_claim": False,
        "truth_boundary": (
            "The platform consumed a hash-verified exact-revision GitHub Release image artifact "
            "produced by the separate AI runtime repository. This is hosted CI evidence only; it "
            "does not claim registry publication, production deployment, or owner-PC validation."
        ),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
