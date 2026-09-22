#!/usr/bin/env python3
"""Hosted runtime proof for the Aetheris core / AI-runtime deployment boundary.

The proof is intentionally executed after CI has built a revision-tagged real
orchestrator image and removed the orchestrator source directory from the
workspace. It then boots the core-only Compose topology, proves core health
without any AI runtime container, attaches the separately managed image with
its runtime prerequisites, and exercises the versioned HTTP boundary through
the authenticated gateway.

This is hosted CI evidence only. It does not claim physical-PC validation or a
production deployment.
"""

from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILE = ROOT / "docker-compose.core.yml"
REPORT_FILE = ROOT / "build-evidence/runtime/core-deployment-boundary-report.json"
NETWORK_NAME = os.environ.get("AETHERIS_CORE_NETWORK", "aetheris-core-boundary")
RUNTIME_CONTAINER = os.environ.get("AETHERIS_EXTERNAL_ORCHESTRATOR_CONTAINER", "orchestrator-runtime")
RUNTIME_IMAGE = os.environ.get("AETHERIS_EXTERNAL_ORCHESTRATOR_IMAGE", "")
RUNTIME_DATASOURCE_URL = os.environ.get(
    "AETHERIS_EXTERNAL_ORCHESTRATOR_DATASOURCE_URL",
    "jdbc:postgresql://postgres:5432/aetheris",
)
RUNTIME_DATASOURCE_USERNAME = os.environ.get(
    "AETHERIS_EXTERNAL_ORCHESTRATOR_DATASOURCE_USERNAME",
    "aetheris",
)
RUNTIME_DATASOURCE_PASSWORD = os.environ.get(
    "AETHERIS_EXTERNAL_ORCHESTRATOR_DATASOURCE_PASSWORD",
    "aetheris",
)
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME evidence proves the core-only Docker Compose path can be configured, "
    "built, and started while orchestrator-service source is absent, and that the gateway "
    "can consume a separately managed real orchestrator image over the versioned HTTP "
    "boundary. It is not production deployment evidence or physical-PC validation."
)


def run(args: list[str], timeout: int = 600) -> str:
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
            f"stdout={result.stdout[-4000:]}\nstderr={result.stderr[-4000:]}"
        )
    return result.stdout.strip()


def compose(*args: str, timeout: int = 600) -> str:
    return run(["docker", "compose", "-f", str(COMPOSE_FILE), *args], timeout=timeout)


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


def wait_http(name: str, url: str, timeout: int = 120) -> None:
    deadline = time.monotonic() + timeout
    last_status = 0
    while time.monotonic() < deadline:
        try:
            request = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(request, timeout=5) as response:
                last_status = response.status
                if 200 <= last_status < 400:
                    return
        except urllib.error.HTTPError as error:
            last_status = error.code
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError):
            last_status = 0
        time.sleep(2)
    raise AssertionError(f"{name} did not become reachable: HTTP {last_status}")


def write_report(
    *,
    status: str,
    checks: dict[str, str],
    revision: str,
    core_services: list[str],
    external_image_id: str,
    error: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_deployment_claim": False,
        "orchestrator_source_present_during_core_boot": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "revision": revision,
        "core_compose_file": str(COMPOSE_FILE.relative_to(ROOT)),
        "core_services": core_services,
        "external_orchestrator": {
            "image_ref": RUNTIME_IMAGE,
            "image_id": external_image_id,
            "container_name": RUNTIME_CONTAINER,
            "network": NETWORK_NAME,
            "datasource_url": RUNTIME_DATASOURCE_URL,
            "datasource_username": RUNTIME_DATASOURCE_USERNAME,
        },
        "checks": dict(sorted(checks.items())),
    }
    if error:
        payload["error"] = error
    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    REPORT_FILE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    checks: dict[str, str] = {}
    core_services: list[str] = []
    external_image_id = ""
    revision = run(["git", "rev-parse", "HEAD"])

    def passed(check_id: str) -> None:
        checks[check_id] = "PASS"

    try:
        expected_revision = os.environ.get("AETHERIS_EXPECTED_REVISION", revision)
        if revision != expected_revision:
            raise AssertionError(
                f"checked-out revision mismatch: expected={expected_revision} actual={revision}"
            )
        passed("revision.exact-head-bound")

        if (ROOT / "orchestrator-service").exists():
            raise AssertionError("orchestrator-service source must be absent before core-only proof starts")
        passed("core.orchestrator-source-absent")

        if not COMPOSE_FILE.is_file():
            raise AssertionError("docker-compose.core.yml is missing")
        core_services = [line for line in compose("config", "--services").splitlines() if line]
        if "orchestrator-service" in core_services:
            raise AssertionError(f"core-only Compose unexpectedly includes orchestrator-service: {core_services}")
        rendered = compose("config")
        if "orchestrator-service" in rendered or "./orchestrator-service" in rendered:
            raise AssertionError("core-only Compose still contains an orchestrator source/service dependency")
        passed("core.compose-excludes-orchestrator-service")

        # No AI-runtime container is allowed to exist while the core path is built and booted.
        existing = run(["docker", "ps", "-a", "--format", "{{.Names}}"])
        if RUNTIME_CONTAINER in existing.splitlines():
            raise AssertionError(f"external runtime container already exists before core boot: {RUNTIME_CONTAINER}")

        compose("up", "-d", "--build", timeout=900)
        for service, port, check_id in [
            ("gateway", 8080, "core.gateway-healthy-without-ai-runtime"),
            ("identity-service", 8082, "core.identity-healthy-without-ai-runtime"),
            ("user-service", 8081, "core.user-healthy-without-ai-runtime"),
            ("audit-service", 8083, "core.audit-healthy-without-ai-runtime"),
        ]:
            wait_health(service, port)
            passed(check_id)
        wait_http("dashboard", "http://127.0.0.1:3000/")
        passed("core.dashboard-reachable-without-ai-runtime")

        if not RUNTIME_IMAGE:
            raise AssertionError("AETHERIS_EXTERNAL_ORCHESTRATOR_IMAGE must identify a supplied runtime image")
        external_image_id = run(["docker", "image", "inspect", RUNTIME_IMAGE, "--format", "{{.Id}}"])
        if not external_image_id.startswith("sha256:"):
            raise AssertionError(f"unexpected external image identity: {external_image_id!r}")
        passed("external.versioned-image-resolved")

        if not RUNTIME_DATASOURCE_URL or not RUNTIME_DATASOURCE_USERNAME or not RUNTIME_DATASOURCE_PASSWORD:
            raise AssertionError("external orchestrator datasource configuration is incomplete")
        run(
            [
                "docker",
                "run",
                "-d",
                "--name",
                RUNTIME_CONTAINER,
                "--network",
                NETWORK_NAME,
                "-p",
                "18090:8090",
                "-e",
                f"SPRING_DATASOURCE_URL={RUNTIME_DATASOURCE_URL}",
                "-e",
                f"SPRING_DATASOURCE_USERNAME={RUNTIME_DATASOURCE_USERNAME}",
                "-e",
                f"SPRING_DATASOURCE_PASSWORD={RUNTIME_DATASOURCE_PASSWORD}",
                RUNTIME_IMAGE,
            ]
        )
        wait_health("external orchestrator", 18090)
        passed("external.orchestrator-healthy")

        container_image_id = run(
            ["docker", "inspect", RUNTIME_CONTAINER, "--format", "{{.Image}}"]
        )
        if container_image_id != external_image_id:
            raise AssertionError(
                f"external runtime image mismatch: expected={external_image_id} actual={container_image_id}"
            )
        networks = json.loads(
            run(["docker", "inspect", RUNTIME_CONTAINER, "--format", "{{json .NetworkSettings.Networks}}"])
        )
        if NETWORK_NAME not in networks:
            raise AssertionError(f"external runtime is not attached to {NETWORK_NAME}: {networks!r}")
        passed("external.runtime-identity-bound")

        status, direct_tasks = http_json("GET", "http://127.0.0.1:18090/api/orchestrator/tasks")
        if status != 200 or not isinstance(direct_tasks, list):
            raise AssertionError(f"direct orchestrator contract read failed: HTTP {status} {direct_tasks!r}")
        passed("boundary.direct-contract-response")

        email = f"core-boundary-{revision[:12]}@aetheris.local"
        status, auth = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Core Boundary Proof", "email": email, "password": "CoreBoundaryProof123!"},
        )
        if status != 201 or not isinstance(auth, dict) or not auth.get("accessToken"):
            raise AssertionError(f"identity registration failed: HTTP {status} {auth!r}")
        headers = {"Authorization": f"Bearer {auth['accessToken']}"}

        status, routed_tasks = http_json(
            "GET", "http://127.0.0.1:8080/api/orchestrator/tasks", headers=headers
        )
        if status != 200 or not isinstance(routed_tasks, list):
            raise AssertionError(f"gateway orchestrator contract read failed: HTTP {status} {routed_tasks!r}")
        passed("boundary.authenticated-gateway-route")

        if routed_tasks != direct_tasks:
            raise AssertionError(
                f"gateway/external runtime contract responses diverged: direct={direct_tasks!r} routed={routed_tasks!r}"
            )
        passed("boundary.gateway-response-matches-runtime")
        passed("truth-boundary.hosted-runtime-only")

        write_report(
            status="PASS",
            checks=checks,
            revision=revision,
            core_services=core_services,
            external_image_id=external_image_id,
        )
        print(
            json.dumps(
                {
                    "status": "PASS",
                    "revision": revision,
                    "checks": len(checks),
                    "core_services": len(core_services),
                    "external_image_id": external_image_id,
                    "orchestrator_source_present_during_core_boot": False,
                },
                sort_keys=True,
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(
            status="FAIL",
            checks=checks,
            revision=revision,
            core_services=core_services,
            external_image_id=external_image_id,
            error=f"{type(exc).__name__}: {exc}",
        )
        raise


if __name__ == "__main__":
    raise SystemExit(main())
