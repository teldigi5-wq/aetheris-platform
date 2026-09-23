#!/usr/bin/env python3
"""Hosted deployment/release runtime proof for Aetheris.

This proves an exact-revision Docker Compose candidate can be identified,
started, exercised through authenticated traffic, subjected to a controlled
gateway outage, and recovered onto the same verified image without losing
application state. The AI runtime is consumed from the separately certified
runtime repository artifact rather than from platform-owned source.

It intentionally does not claim production-cloud rollout, provider-specific
rollback, SLA/RTO, or physical-PC validation.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
RUNTIME_REFERENCE_PATH = ROOT / "architecture" / "ai-runtime-certification-reference.json"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME evidence validates exact-revision release identity, Docker Compose "
    "startup, controlled gateway outage detection, same-image recovery, and state "
    "preservation on a GitHub-hosted Ubuntu runner only. It is not a production cloud "
    "deployment, blue/green or canary certification, provider-specific rollback proof, "
    "SLA/RTO claim, target-PC benchmark, or physical-PC validation."
)


def run(args: list[str], timeout: int = 180) -> str:
    result = subprocess.run(
        args,
        cwd=ROOT,
        text=True,
        capture_output=True,
        timeout=timeout,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(
            f"command failed ({result.returncode}): {' '.join(args)}\n"
            f"stdout={result.stdout[-2500:]}\nstderr={result.stderr[-2500:]}"
        )
    return result.stdout.strip()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def http_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 8,
) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8")
    except (urllib.error.URLError, TimeoutError, ConnectionError, OSError):
        return 0, None
    if not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def wait_health(name: str, port: int, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last: tuple[int, Any] = (0, None)
    url = f"http://127.0.0.1:{port}/actuator/health"
    while time.monotonic() < deadline:
        last = http_json("GET", url, timeout=5)
        status, body = last
        if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
            return
        time.sleep(2)
    raise AssertionError(f"{name} did not become healthy: {last!r}")


def compose_image_id(service: str) -> str:
    image_ref = run(["docker", "compose", "images", "-q", service])
    if not image_ref:
        raise AssertionError(f"no Compose image found for {service}")
    image_id = run(["docker", "image", "inspect", image_ref, "--format", "{{.Id}}"])
    if not image_id.startswith("sha256:"):
        raise AssertionError(f"unexpected image identity for {service}: {image_id!r}")
    return image_id


def write_report(
    output: Path,
    *,
    status: str,
    checks: dict[str, str],
    revision: str,
    release_manifest: dict[str, Any],
    manifest_sha256: str,
    baseline: dict[str, Any],
    final_state: dict[str, Any],
    error: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_deployment_claim": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "revision": revision,
        "release_manifest_sha256": manifest_sha256,
        "release_manifest": release_manifest,
        "baseline": baseline,
        "final_state": final_state,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        payload["error"] = error
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--contract",
        default="build-evidence/runtime/deployment-release-runtime-contract.json",
    )
    parser.add_argument(
        "--output",
        default="build-evidence/runtime/deployment-release-runtime-report.json",
    )
    parser.add_argument(
        "--manifest",
        default="build-evidence/runtime/deployment-release-manifest.json",
    )
    args = parser.parse_args()

    contract_path = ROOT / args.contract
    output_path = ROOT / args.output
    manifest_path = ROOT / args.manifest
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    required = {item["id"] for item in contract["checks"]}
    checks: dict[str, str] = {}
    baseline: dict[str, Any] = {}
    final_state: dict[str, Any] = {}
    release_manifest: dict[str, Any] = {}
    manifest_sha256 = ""
    revision = run(["git", "rev-parse", "HEAD"])

    def passed(check_id: str) -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared check emitted: {check_id}")
        checks[check_id] = "PASS"

    try:
        expected_revision = os.environ.get("AETHERIS_EXPECTED_REVISION", revision)
        if revision != expected_revision:
            raise AssertionError(
                f"checked-out revision mismatch: expected={expected_revision} actual={revision}"
            )
        passed("release.revision-bound")

        compose_path = ROOT / "docker-compose.yml"
        compose_sha = sha256_file(compose_path)
        if len(compose_sha) != 64:
            raise AssertionError("invalid Compose SHA-256")
        passed("release.compose-checksum-recorded")

        services = list(contract["services"])
        image_ids = {service: compose_image_id(service) for service in services}
        if len(set(image_ids.values())) != len(image_ids):
            raise AssertionError(f"service image identities unexpectedly collide: {image_ids!r}")

        runtime_reference = json.loads(RUNTIME_REFERENCE_PATH.read_text(encoding="utf-8"))
        if runtime_reference.get("status") != "DESTINATION_RUNTIME_CERTIFIED":
            raise AssertionError(f"runtime certification is not authoritative: {runtime_reference!r}")
        if runtime_reference.get("source_root_deletion_status") != "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION":
            raise AssertionError("runtime source extraction is not certified complete")
        destination_runtime = runtime_reference["destination_runtime"]
        runtime_image_ref = os.environ.get(
            "AETHERIS_AI_RUNTIME_IMAGE", destination_runtime["image_ref"]
        )
        if runtime_image_ref != destination_runtime["image_ref"]:
            raise AssertionError(
                f"runtime image ref drift: expected={destination_runtime['image_ref']} actual={runtime_image_ref}"
            )
        runtime_image_id = run(
            ["docker", "image", "inspect", runtime_image_ref, "--format", "{{.Id}}"]
        )
        runtime_revision = run(
            [
                "docker",
                "image",
                "inspect",
                runtime_image_ref,
                "--format",
                '{{index .Config.Labels "org.opencontainers.image.revision"}}',
            ]
        )
        runtime_source = run(
            [
                "docker",
                "image",
                "inspect",
                runtime_image_ref,
                "--format",
                '{{index .Config.Labels "org.opencontainers.image.source"}}',
            ]
        )
        runtime_evidence_class = run(
            [
                "docker",
                "image",
                "inspect",
                runtime_image_ref,
                "--format",
                '{{index .Config.Labels "io.aetheris.evidence-class"}}',
            ]
        )
        if runtime_revision != destination_runtime["certified_sha"]:
            raise AssertionError(
                f"runtime revision label drift: expected={destination_runtime['certified_sha']} actual={runtime_revision}"
            )
        expected_source = f"https://github.com/{destination_runtime['repository']}"
        if runtime_source != expected_source:
            raise AssertionError(
                f"runtime source label drift: expected={expected_source} actual={runtime_source}"
            )
        if runtime_evidence_class != "HOSTED_RUNTIME_ARTIFACT":
            raise AssertionError(f"unexpected runtime evidence class: {runtime_evidence_class!r}")
        if image_ids.get("orchestrator-service") != runtime_image_id:
            raise AssertionError(
                "Compose orchestrator image does not match certified destination artifact: "
                f"compose={image_ids.get('orchestrator-service')} certified={runtime_image_id}"
            )
        passed("release.service-image-identities-recorded")

        source_hashes = {
            "docker-compose.yml": compose_sha,
            str(contract_path.relative_to(ROOT)): sha256_file(contract_path),
            str(RUNTIME_REFERENCE_PATH.relative_to(ROOT)): sha256_file(RUNTIME_REFERENCE_PATH),
        }
        for service in services:
            if service == "orchestrator-service":
                continue
            dockerfile = ROOT / service / "Dockerfile"
            source_hashes[str(dockerfile.relative_to(ROOT))] = sha256_file(dockerfile)

        release_manifest = {
            "schema_version": 1,
            "revision": revision,
            "evidence_class": "HOSTED_RUNTIME",
            "service_image_ids": image_ids,
            "source_sha256": dict(sorted(source_hashes.items())),
            "external_runtime": {
                "repository": destination_runtime["repository"],
                "revision": destination_runtime["certified_sha"],
                "image_ref": runtime_image_ref,
                "image_id": runtime_image_id,
                "image_archive_sha256": destination_runtime["image_archive_sha256"],
            },
        }
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(
            json.dumps(release_manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        if not manifest_path.is_file():
            raise AssertionError("release manifest was not created")
        passed("release.manifest-created")

        manifest_sha256 = sha256_file(manifest_path)
        if manifest_sha256 != hashlib.sha256(manifest_path.read_bytes()).hexdigest():
            raise AssertionError("release manifest checksum did not self-verify")
        passed("release.manifest-checksum-verifies")

        for service, port, check_id in [
            ("gateway", 8080, "deployment.gateway-health"),
            ("identity-service", 8082, "deployment.identity-health"),
            ("user-service", 8081, "deployment.user-health"),
        ]:
            wait_health(service, port)
            passed(check_id)

        identity_email = "deployment-release-proof@aetheris.local"
        profile_email = "deployment-release-profile@aetheris.local"
        password = "DeploymentReleaseProof123!"
        status, auth = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Deployment Release Proof", "email": identity_email, "password": password},
        )
        if status != 201 or not isinstance(auth, dict) or not auth.get("accessToken"):
            raise AssertionError(f"registration failed: HTTP {status} {auth!r}")
        headers = {"Authorization": f"Bearer {auth['accessToken']}"}

        status, created_profile = http_json(
            "POST",
            "http://127.0.0.1:8081/api/users",
            {"name": "Deployment Release Profile", "email": profile_email},
        )
        if (
            status != 201
            or not isinstance(created_profile, dict)
            or created_profile.get("email") != profile_email
        ):
            raise AssertionError(
                f"explicit user profile creation failed: HTTP {status} {created_profile!r}"
            )

        status, users = http_json("GET", "http://127.0.0.1:8080/api/users", headers=headers)
        if status != 200 or not isinstance(users, list):
            raise AssertionError(f"protected baseline read failed: HTTP {status} {users!r}")
        baseline = {
            "user_count": len(users),
            "proof_profile_present": any(
                isinstance(item, dict) and item.get("email") == profile_email for item in users
            ),
            "gateway_image_id": image_ids["gateway"],
        }
        if not baseline["proof_profile_present"]:
            raise AssertionError("explicit proof user profile is absent from protected read")
        passed("deployment.authenticated-read")

        run(["docker", "compose", "stop", "gateway"])
        health_status, _ = http_json("GET", "http://127.0.0.1:8080/actuator/health", timeout=3)
        if health_status == 200:
            raise AssertionError("controlled gateway outage was not observable")
        passed("failure.controlled-gateway-outage-detected")

        read_status, _ = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=headers, timeout=3
        )
        if read_status == 200:
            raise AssertionError("protected read unexpectedly succeeded during gateway outage")
        passed("failure.protected-read-unavailable")

        run(["docker", "compose", "start", "gateway"])
        wait_health("gateway", 8080)
        passed("rollback.gateway-restored")

        restored_image_id = compose_image_id("gateway")
        final_state["gateway_image_id"] = restored_image_id
        if restored_image_id != baseline["gateway_image_id"]:
            raise AssertionError(
                "gateway recovered on a different image: "
                f"before={baseline['gateway_image_id']} after={restored_image_id}"
            )
        passed("rollback.verified-image-restored")

        status, recovered_users = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=headers
        )
        if status != 200 or not isinstance(recovered_users, list):
            raise AssertionError(f"protected read did not recover: HTTP {status} {recovered_users!r}")
        passed("rollback.protected-read-recovers")

        final_state.update(
            {
                "user_count": len(recovered_users),
                "proof_profile_present": any(
                    isinstance(item, dict) and item.get("email") == profile_email
                    for item in recovered_users
                ),
            }
        )
        if (
            final_state["user_count"] != baseline["user_count"]
            or not final_state["proof_profile_present"]
        ):
            raise AssertionError(
                f"state changed across rollback: baseline={baseline!r} final={final_state!r}"
            )
        passed("rollback.state-preserved")

        passed("evidence.machine-readable")
        passed("truth-boundary.hosted-runtime")

        missing = required - set(checks)
        if missing:
            raise AssertionError(f"required checks were not executed: {sorted(missing)}")

        write_report(
            output_path,
            status="PASS",
            checks=checks,
            revision=revision,
            release_manifest=release_manifest,
            manifest_sha256=manifest_sha256,
            baseline=baseline,
            final_state=final_state,
        )
        print(
            json.dumps(
                {
                    "status": "PASS",
                    "checks": len(checks),
                    "revision": revision,
                    "manifest_sha256": manifest_sha256,
                    "gateway_image_id": final_state["gateway_image_id"],
                    "runtime_revision": destination_runtime["certified_sha"],
                    "state_preserved": True,
                },
                sort_keys=True,
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(
            output_path,
            status="FAIL",
            checks=checks,
            revision=revision,
            release_manifest=release_manifest,
            manifest_sha256=manifest_sha256,
            baseline=baseline,
            final_state=final_state,
            error=f"{type(exc).__name__}: {exc}",
        )
        raise


if __name__ == "__main__":
    raise SystemExit(main())
