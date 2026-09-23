#!/usr/bin/env python3
"""Hosted proof that the source-absent platform consumes the certified external AI runtime artifact."""

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
COMPOSE_FILE = ROOT / "docker-compose.integration-external.yml"
REFERENCE_FILE = ROOT / "architecture" / "ai-runtime-certification-reference.json"
REPORT_FILE = ROOT / "build-evidence/runtime/external-integration-runtime-report.json"
AI_SOURCE_DIRS = (
    "orchestrator-service",
    "aetheris-quant",
    "aetheris-reasoning",
    "workstation-agent",
)
RUNTIME_IMAGE = os.environ.get("AETHERIS_AI_RUNTIME_IMAGE", "")
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME evidence proves the source-absent platform can build and boot its full "
    "Docker Compose integration topology while consuming the exact certified AI-runtime "
    "GitHub Release image artifact. It validates the versioned HTTP boundary and Prometheus "
    "visibility on a GitHub-hosted Ubuntu runner only. It is not a production deployment, "
    "container-registry publication, physical-PC validation, or target-PC benchmark."
)


def run(args: list[str], timeout: int = 900) -> str:
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
            f"stdout={result.stdout[-5000:]}\nstderr={result.stderr[-5000:]}"
        )
    return result.stdout.strip()


def compose(*args: str, timeout: int = 900) -> str:
    return run(
        ["docker", "compose", "-f", str(COMPOSE_FILE), "--profile", "observability", *args],
        timeout=timeout,
    )


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


def wait_health(name: str, port: int, timeout: int = 240) -> None:
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


def wait_http(name: str, url: str, timeout: int = 180) -> None:
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


def wait_prometheus_runtime_target(timeout: int = 180) -> dict[str, Any]:
    deadline = time.monotonic() + timeout
    last: Any = None
    url = "http://127.0.0.1:9090/api/v1/targets"
    while time.monotonic() < deadline:
        status, body = http_json("GET", url, timeout=8)
        last = body
        if status == 200 and isinstance(body, dict) and body.get("status") == "success":
            for target in body.get("data", {}).get("activeTargets", []):
                labels = target.get("labels", {})
                discovered = target.get("discoveredLabels", {})
                job = labels.get("job") or discovered.get("__meta_prometheus_job_name")
                scrape_url = target.get("scrapeUrl", "")
                if (
                    job == "aetheris-orchestrator-service"
                    and "orchestrator-service:8090" in scrape_url
                    and target.get("health") == "up"
                ):
                    return target
        time.sleep(3)
    raise AssertionError(f"Prometheus never reported AI runtime target UP: {last!r}")


def write_report(
    *,
    status: str,
    platform_revision: str,
    runtime_revision: str,
    runtime_repository: str,
    checks: dict[str, str],
    runtime_image_id: str,
    runtime_container_image_id: str,
    services: list[str],
    prometheus_target: dict[str, Any] | None,
    error: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "schema_version": 2,
        "evidence_class": "HOSTED_RUNTIME",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_deployment_claim": False,
        "registry_publication_claim": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "platform_revision": platform_revision,
        "runtime_revision": runtime_revision,
        "runtime_repository": runtime_repository,
        "compose_file": str(COMPOSE_FILE.relative_to(ROOT)),
        "ai_source_directories_present": [
            path for path in AI_SOURCE_DIRS if (ROOT / path).exists()
        ],
        "runtime_image": {
            "ref": RUNTIME_IMAGE,
            "image_id": runtime_image_id,
            "container_image_id": runtime_container_image_id,
        },
        "services": services,
        "prometheus_runtime_target": prometheus_target,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        payload["error"] = error
    REPORT_FILE.parent.mkdir(parents=True, exist_ok=True)
    REPORT_FILE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    checks: dict[str, str] = {}
    services: list[str] = []
    runtime_image_id = ""
    runtime_container_image_id = ""
    prometheus_target: dict[str, Any] | None = None
    platform_revision = run(["git", "rev-parse", "HEAD"])
    reference = json.loads(REFERENCE_FILE.read_text(encoding="utf-8"))
    runtime = reference["destination_runtime"]
    runtime_revision = runtime["certified_sha"]
    runtime_repository = runtime["repository"]

    def passed(check_id: str) -> None:
        checks[check_id] = "PASS"

    try:
        expected_platform_revision = os.environ.get("AETHERIS_EXPECTED_REVISION", platform_revision)
        if platform_revision != expected_platform_revision:
            raise AssertionError(
                f"checked-out platform revision mismatch: expected={expected_platform_revision} actual={platform_revision}"
            )
        passed("revision.exact-platform-head-bound")

        if reference.get("status") != "DESTINATION_RUNTIME_CERTIFIED":
            raise AssertionError(f"runtime reference is not certified: {reference!r}")
        if reference.get("source_root_deletion_status") != "SOURCE_EXTRACTED_TO_CERTIFIED_DESTINATION":
            raise AssertionError(f"runtime source extraction is not finalized: {reference!r}")
        if reference.get("platform_runtime_source_present") is not False:
            raise AssertionError(f"platform runtime source boundary drifted: {reference!r}")
        passed("ownership.certified-destination-runtime")

        present = [path for path in AI_SOURCE_DIRS if (ROOT / path).exists()]
        if present:
            raise AssertionError(f"AI runtime source must be absent before proof starts: {present}")
        passed("source.all-ai-runtime-trees-absent")

        if not COMPOSE_FILE.is_file():
            raise AssertionError(f"missing external integration Compose: {COMPOSE_FILE.name}")
        expected_image = runtime["image_ref"]
        if RUNTIME_IMAGE != expected_image:
            raise AssertionError(f"runtime image mismatch: expected={expected_image!r} actual={RUNTIME_IMAGE!r}")
        if runtime_revision not in RUNTIME_IMAGE:
            raise AssertionError(f"runtime image is not exact-revision tagged: {RUNTIME_IMAGE!r}")
        passed("runtime.image-ref-certified-revision-tagged")

        rendered_json = json.loads(compose("config", "--format", "json"))
        service_config = rendered_json.get("services", {})
        services = sorted(service_config)
        orchestrator = service_config.get("orchestrator-service")
        if not isinstance(orchestrator, dict):
            raise AssertionError("external integration Compose is missing orchestrator-service")
        if "build" in orchestrator:
            raise AssertionError(f"external orchestrator must not have a build context: {orchestrator['build']!r}")
        if orchestrator.get("image") != RUNTIME_IMAGE:
            raise AssertionError(
                f"rendered orchestrator image mismatch: {orchestrator.get('image')!r} != {RUNTIME_IMAGE!r}"
            )
        rendered_text = compose("config")
        forbidden_source_tokens = (
            "./orchestrator-service",
            "aetheris-quant",
            "aetheris-reasoning",
            "workstation-agent",
        )
        leaked = [token for token in forbidden_source_tokens if token in rendered_text]
        if leaked:
            raise AssertionError(f"external integration Compose still references AI source: {leaked}")
        passed("compose.external-runtime-has-no-source-build")
        passed("compose.full-integration-config-renders-source-absent")

        runtime_image_id = run(["docker", "image", "inspect", RUNTIME_IMAGE, "--format", "{{.Id}}"])
        if not runtime_image_id.startswith("sha256:"):
            raise AssertionError(f"unexpected AI runtime image identity: {runtime_image_id!r}")
        label_revision = run([
            "docker", "image", "inspect", RUNTIME_IMAGE, "--format",
            '{{index .Config.Labels "org.opencontainers.image.revision"}}',
        ])
        label_source = run([
            "docker", "image", "inspect", RUNTIME_IMAGE, "--format",
            '{{index .Config.Labels "org.opencontainers.image.source"}}',
        ])
        label_evidence = run([
            "docker", "image", "inspect", RUNTIME_IMAGE, "--format",
            '{{index .Config.Labels "io.aetheris.evidence-class"}}',
        ])
        if label_revision != runtime_revision:
            raise AssertionError(f"runtime provenance revision mismatch: {label_revision!r}")
        if label_source != f"https://github.com/{runtime_repository}":
            raise AssertionError(f"runtime provenance source mismatch: {label_source!r}")
        if label_evidence != runtime["artifact_evidence_class"]:
            raise AssertionError(f"runtime evidence class mismatch: {label_evidence!r}")
        passed("runtime.release-artifact-provenance-verified")

        compose("up", "-d", "--build", timeout=1200)
        passed("compose.full-integration-started")

        for service, port, check_id in [
            ("gateway", 8080, "health.gateway"),
            ("identity-service", 8082, "health.identity"),
            ("user-service", 8081, "health.user"),
            ("audit-service", 8083, "health.audit"),
            ("orchestrator-service", 8090, "health.external-ai-runtime"),
        ]:
            wait_health(service, port)
            passed(check_id)

        wait_http("dashboard", "http://127.0.0.1:3000/")
        passed("health.dashboard")
        wait_http("prometheus", "http://127.0.0.1:9090/-/ready")
        passed("observability.prometheus-ready")

        container_id = compose("ps", "-q", "orchestrator-service")
        if not container_id:
            raise AssertionError("orchestrator-service container is missing")
        runtime_container_image_id = run(["docker", "inspect", container_id, "--format", "{{.Image}}"])
        if runtime_container_image_id != runtime_image_id:
            raise AssertionError(
                f"runtime container image mismatch: expected={runtime_image_id} actual={runtime_container_image_id}"
            )
        passed("runtime.container-bound-to-certified-release-image")

        status, direct_tasks = http_json("GET", "http://127.0.0.1:8090/api/orchestrator/tasks")
        if status != 200 or not isinstance(direct_tasks, list):
            raise AssertionError(
                f"direct external AI runtime contract read failed: HTTP {status} {direct_tasks!r}"
            )
        passed("contract.direct-ai-runtime-response")

        email = f"external-integration-{platform_revision[:12]}@aetheris.local"
        status, auth = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {
                "name": "External Integration Proof",
                "email": email,
                "password": "ExternalIntegrationProof123!",
            },
        )
        if status != 201 or not isinstance(auth, dict) or not auth.get("accessToken"):
            raise AssertionError(f"identity registration failed: HTTP {status} {auth!r}")
        headers = {"Authorization": f"Bearer {auth['accessToken']}"}

        status, routed_tasks = http_json(
            "GET",
            "http://127.0.0.1:8080/api/orchestrator/tasks",
            headers=headers,
        )
        if status != 200 or not isinstance(routed_tasks, list):
            raise AssertionError(
                f"gateway external AI runtime contract read failed: HTTP {status} {routed_tasks!r}"
            )
        passed("contract.authenticated-gateway-route")

        if routed_tasks != direct_tasks:
            raise AssertionError(
                f"gateway/runtime contract responses diverged: direct={direct_tasks!r} routed={routed_tasks!r}"
            )
        passed("contract.gateway-response-matches-runtime")

        prometheus_target = wait_prometheus_runtime_target()
        passed("observability.ai-runtime-target-up")
        passed("truth-boundary.hosted-runtime-only")

        write_report(
            status="PASS",
            platform_revision=platform_revision,
            runtime_revision=runtime_revision,
            runtime_repository=runtime_repository,
            checks=checks,
            runtime_image_id=runtime_image_id,
            runtime_container_image_id=runtime_container_image_id,
            services=services,
            prometheus_target=prometheus_target,
        )
        print(json.dumps({
            "status": "PASS",
            "platform_revision": platform_revision,
            "runtime_revision": runtime_revision,
            "checks": len(checks),
            "services": len(services),
            "runtime_image_id": runtime_image_id,
            "ai_source_directories_present": [],
        }, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(
            status="FAIL",
            platform_revision=platform_revision,
            runtime_revision=runtime_revision,
            runtime_repository=runtime_repository,
            checks=checks,
            runtime_image_id=runtime_image_id,
            runtime_container_image_id=runtime_container_image_id,
            services=services,
            prometheus_target=prometheus_target,
            error=f"{type(exc).__name__}: {exc}",
        )
        raise


if __name__ == "__main__":
    raise SystemExit(main())
