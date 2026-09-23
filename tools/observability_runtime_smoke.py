#!/usr/bin/env python3
"""Hosted observability proof for Aetheris.

This validates the live observability wiring in a GitHub-hosted Docker Compose
run. It does not claim production or physical-PC validation.
"""

from __future__ import annotations

import argparse
import base64
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TRUTH_BOUNDARY = (
    "HOSTED_OBSERVABILITY evidence proves selected metrics/logs/traces/dashboard wiring in a "
    "GitHub-hosted Docker Compose environment only. It is not production readiness or physical-PC validation."
)


def run(args: list[str], timeout: int = 120) -> str:
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=True, timeout=timeout, check=False)
    if result.returncode != 0:
        raise AssertionError(
            f"command failed ({result.returncode}): {' '.join(args)}\n"
            f"stdout={result.stdout[-2000:]}\nstderr={result.stderr[-2000:]}"
        )
    return result.stdout.strip()


def request(
    url: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 10,
) -> tuple[int, str]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8")


def json_request(url: str, **kwargs: Any) -> tuple[int, Any]:
    status, raw = request(url, **kwargs)
    if not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def wait_http(url: str, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last = "not attempted"
    while time.monotonic() < deadline:
        try:
            status, raw = request(url, timeout=5)
            last = f"HTTP {status}: {raw[:200]!r}"
            if 200 <= status < 300:
                return
        except Exception as exc:  # noqa: BLE001
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"endpoint did not become ready: {url}; last={last}")


def wait_prometheus_targets(expected_jobs: set[str], timeout: int = 120) -> dict[str, str]:
    deadline = time.monotonic() + timeout
    last: dict[str, str] = {}
    while time.monotonic() < deadline:
        status, body = json_request("http://127.0.0.1:9090/api/v1/targets")
        if status == 200 and isinstance(body, dict) and body.get("status") == "success":
            active = body.get("data", {}).get("activeTargets", [])
            states: dict[str, str] = {}
            for target in active:
                job = target.get("labels", {}).get("job")
                if job:
                    states[job] = target.get("health", "unknown")
            last = states
            if all(states.get(job) == "up" for job in expected_jobs):
                return states
        time.sleep(3)
    raise AssertionError(f"Prometheus targets did not all become healthy: {last}")


def prometheus_scalar(query: str) -> float:
    encoded = urllib.parse.quote(query)
    status, body = json_request(f"http://127.0.0.1:9090/api/v1/query?query={encoded}")
    if status != 200 or not isinstance(body, dict) or body.get("status") != "success":
        raise AssertionError(f"Prometheus query failed: status={status}, body={body!r}")
    result = body.get("data", {}).get("result", [])
    if not result:
        return 0.0
    return sum(float(item["value"][1]) for item in result if "value" in item)


def metric_sum(text: str, metric_name: str) -> float:
    total = 0.0
    found = False
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line == metric_name or line.startswith(metric_name + "{"):
            parts = line.rsplit(" ", 1)
            if len(parts) == 2:
                total += float(parts[1])
                found = True
    if not found:
        raise AssertionError(f"metric not found: {metric_name}")
    return total


def wait_tempo_spans(timeout: int = 120) -> float:
    deadline = time.monotonic() + timeout
    last = "no metrics"
    while time.monotonic() < deadline:
        status, metrics = request("http://127.0.0.1:3200/metrics")
        if status == 200:
            try:
                value = metric_sum(metrics, "tempo_distributor_spans_received_total")
                last = str(value)
                if value > 0:
                    return value
            except Exception as exc:  # noqa: BLE001
                last = repr(exc)
        time.sleep(3)
    raise AssertionError(f"Tempo did not report received spans; last={last}")


def wait_loki_service_label(timeout: int = 120) -> list[str]:
    deadline = time.monotonic() + timeout
    last: Any = None
    url = "http://127.0.0.1:3100/loki/api/v1/label/service/values"
    while time.monotonic() < deadline:
        status, body = json_request(url)
        last = body
        if status == 200 and isinstance(body, dict) and body.get("status") == "success":
            values = body.get("data", [])
            if any(name in values for name in ("gateway", "identity-service", "user-service", "audit-service")):
                return values
        time.sleep(3)
    raise AssertionError(f"Loki never exposed expected service labels; last={last!r}")


def grafana_headers() -> dict[str, str]:
    token = base64.b64encode(b"aetheris:aetheris").decode("ascii")
    return {"Authorization": f"Basic {token}"}


def write_report(path: Path, checks: dict[str, Any], status: str, error: str | None = None) -> None:
    report: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_OBSERVABILITY",
        "environment": "github-hosted-ubuntu-docker-compose-observability-profile",
        "physical_pc_validation": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        report["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="build-evidence/observability/observability-runtime-contract.json")
    parser.add_argument("--output", default="build-evidence/observability/observability-runtime-report.json")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required = {item["id"] for item in contract["checks"]}
    checks: dict[str, Any] = {}

    def passed(check_id: str, evidence: Any = "PASS") -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared observability check: {check_id}")
        checks[check_id] = evidence

    try:
        # Readiness for the observability services.
        wait_http("http://127.0.0.1:9090/-/ready")
        passed("prometheus.ready")
        wait_http("http://127.0.0.1:3100/ready")
        passed("loki.ready")
        wait_http("http://127.0.0.1:3200/ready")
        passed("tempo.ready")
        wait_http("http://127.0.0.1:3001/api/health")
        passed("grafana.ready")

        running = set(run(["docker", "compose", "--profile", "observability", "ps", "--status", "running", "--services"]).splitlines())
        for service, check_id in [
            ("otel-collector", "otel-collector.running"),
            ("alloy", "alloy.running"),
        ]:
            if service not in running:
                raise AssertionError(f"expected observability service is not running: {service}; running={sorted(running)}")
            passed(check_id)

        expected_jobs = {
            "aetheris-gateway",
            "aetheris-user-service",
            "aetheris-identity-service",
            "aetheris-audit-service",
            "aetheris-orchestrator-service",
        }
        states = wait_prometheus_targets(expected_jobs)
        passed("prometheus.core-targets-up", {job: states[job] for job in sorted(expected_jobs)})

        # Generate representative traffic through both public and direct health surfaces.
        email = "observability-proof@aetheris.local"
        status, registered = json_request(
            "http://127.0.0.1:8080/api/auth/register",
            method="POST",
            payload={"name": "Observability Proof", "email": email, "password": "ObservabilityProof123!"},
        )
        if status != 201 or not isinstance(registered, dict) or not registered.get("accessToken"):
            raise AssertionError(f"failed to generate authenticated traffic: status={status}, body={registered!r}")
        token = registered["accessToken"]
        headers = {"Authorization": f"Bearer {token}"}
        for _ in range(4):
            json_request("http://127.0.0.1:8080/api/users", headers=headers)
            json_request("http://127.0.0.1:8082/actuator/health")
            json_request("http://127.0.0.1:8083/actuator/health")
            time.sleep(0.5)
        passed("traffic.generated")

        deadline = time.monotonic() + 60
        metric_value = 0.0
        while time.monotonic() < deadline:
            metric_value = prometheus_scalar("sum(http_server_requests_seconds_count)")
            if metric_value > 0:
                break
            time.sleep(3)
        if metric_value <= 0:
            raise AssertionError("Prometheus did not expose positive HTTP request counters")
        passed("prometheus.http-metrics-query", {"http_server_requests_seconds_count_sum": metric_value})

        spans = wait_tempo_spans()
        passed("tempo.received-spans", {"tempo_distributor_spans_received_total": spans})

        loki_services = wait_loki_service_label()
        passed("loki.received-container-logs", {"service_labels": sorted(loki_services)})

        auth = grafana_headers()
        status, datasources = json_request("http://127.0.0.1:3001/api/datasources", headers=auth)
        if status != 200 or not isinstance(datasources, list):
            raise AssertionError(f"Grafana datasource API failed: status={status}, body={datasources!r}")
        names = {item.get("name") for item in datasources if isinstance(item, dict)}
        expected_sources = {"Prometheus", "Loki", "Tempo"}
        if not expected_sources.issubset(names):
            raise AssertionError(f"Grafana datasources missing; expected={expected_sources}, actual={names}")
        passed("grafana.datasources-provisioned", {"datasources": sorted(expected_sources)})

        status, dashboards = json_request("http://127.0.0.1:3001/api/search?type=dash-db", headers=auth)
        if status != 200 or not isinstance(dashboards, list) or not dashboards:
            raise AssertionError(f"Grafana dashboard provisioning not visible: status={status}, body={dashboards!r}")
        passed(
            "grafana.dashboard-provisioned",
            {"dashboards": sorted(item.get("title", "") for item in dashboards if isinstance(item, dict))},
        )

        missing = sorted(required - set(checks))
        extra = sorted(set(checks) - required)
        if missing or extra:
            raise AssertionError(f"observability contract mismatch missing={missing} extra={extra}")

        write_report(ROOT / args.output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(ROOT / args.output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
