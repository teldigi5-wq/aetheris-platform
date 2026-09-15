#!/usr/bin/env python3
"""Hosted performance and load proof for the Aetheris core runtime.

This harness intentionally measures only the GitHub-hosted Docker Compose
environment. It is a regression signal, not a production capacity benchmark,
SLA, target-PC result, or physical-hardware certification.

The Aetheris gateway deliberately rate-limits authenticated user traffic. The
benchmark therefore uses multiple real authenticated principals for aggregate
load while separately proving that a single-principal overload is rejected by
the configured rate limiter.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME performance evidence measures the checked Aetheris behavior on a "
    "GitHub-hosted Ubuntu Docker Compose runner only. Results are regression-oriented "
    "and are not production capacity, SLA/SLO, physical-PC, network-edge, or final "
    "deployment benchmarks."
)


def run_command(args: list[str], timeout: int = 120) -> str:
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
            f"stdout={result.stdout[-2000:]}\nstderr={result.stderr[-2000:]}"
        )
    return result.stdout.strip()


def http_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 10,
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
    if not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def wait_health(name: str, port: int, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last: str = "not attempted"
    url = f"http://127.0.0.1:{port}/actuator/health"
    while time.monotonic() < deadline:
        try:
            status, body = http_json("GET", url, timeout=5)
            last = f"HTTP {status} body={body!r}"
            if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"{name} did not become healthy: {last}")


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, math.ceil(quantile * len(ordered)) - 1)
    return ordered[index]


def request_once(url: str, headers: dict[str, str]) -> tuple[bool, float, int | str]:
    started = time.perf_counter()
    try:
        status, _ = http_json("GET", url, headers=headers, timeout=10)
        elapsed_ms = (time.perf_counter() - started) * 1000
        return status == 200, elapsed_ms, status
    except Exception as exc:  # noqa: BLE001
        elapsed_ms = (time.perf_counter() - started) * 1000
        return False, elapsed_ms, type(exc).__name__


def register_principal(email: str, password: str) -> dict[str, str]:
    status, body = http_json(
        "POST",
        "http://127.0.0.1:8080/api/auth/register",
        {"name": "Performance Proof", "email": email, "password": password},
    )
    if status != 201 or not isinstance(body, dict) or not body.get("accessToken"):
        raise AssertionError(f"principal registration failed for {email}: HTTP {status} {body!r}")
    return {"Authorization": f"Bearer {body['accessToken']}"}


def register_principal_pool(prefix: str, count: int, password: str) -> list[dict[str, str]]:
    """Register below the identity route's 5-token/s per-IP replenish policy."""
    headers_pool: list[dict[str, str]] = []
    for index in range(count):
        email = f"performance-{prefix}-{index:03d}@aetheris.local"
        headers_pool.append(register_principal(email, password))
        # Identity registration is IP-keyed before authentication. Keep setup traffic
        # below its configured replenish rate instead of treating setup 429s as load.
        time.sleep(0.26)
    return headers_pool


def execute_profile(
    name: str,
    *,
    url: str,
    headers_pool: list[dict[str, str]],
    total_requests: int,
    workers: int,
    health_monitor: bool = False,
) -> tuple[dict[str, Any], dict[str, Any] | None]:
    if not headers_pool:
        raise AssertionError(f"profile {name} has no authenticated principals")

    stop_event = threading.Event()
    health_samples: dict[str, Any] | None = None

    def monitor() -> None:
        samples = 0
        failures = 0
        latencies: list[float] = []
        while not stop_event.is_set():
            started = time.perf_counter()
            try:
                status, body = http_json("GET", "http://127.0.0.1:8080/actuator/health", timeout=3)
                ok = status == 200 and isinstance(body, dict) and body.get("status") == "UP"
            except Exception:  # noqa: BLE001
                ok = False
            latencies.append((time.perf_counter() - started) * 1000)
            samples += 1
            if not ok:
                failures += 1
            stop_event.wait(0.35)
        nonlocal health_samples
        health_samples = {
            "samples": samples,
            "failures": failures,
            "max_latency_ms": round(max(latencies), 3) if latencies else 0.0,
        }

    monitor_thread: threading.Thread | None = None
    if health_monitor:
        monitor_thread = threading.Thread(target=monitor, name="gateway-health-monitor", daemon=True)
        monitor_thread.start()

    started = time.perf_counter()
    observations: list[tuple[bool, float, int | str]] = []
    try:
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [
                pool.submit(request_once, url, headers_pool[index % len(headers_pool)])
                for index in range(total_requests)
            ]
            for future in concurrent.futures.as_completed(futures):
                observations.append(future.result())
    finally:
        stop_event.set()
        if monitor_thread is not None:
            monitor_thread.join(timeout=5)

    duration_s = max(time.perf_counter() - started, 0.000001)
    successes = sum(1 for ok, _, _ in observations if ok)
    errors = len(observations) - successes
    latencies = [latency for _, latency, _ in observations]
    outcomes: dict[str, int] = {}
    for _, _, outcome in observations:
        key = str(outcome)
        outcomes[key] = outcomes.get(key, 0) + 1

    metrics = {
        "name": name,
        "workers": workers,
        "authenticated_principals": len(headers_pool),
        "total_requests": len(observations),
        "successful_requests": successes,
        "errors": errors,
        "error_rate": round(errors / max(len(observations), 1), 6),
        "duration_seconds": round(duration_s, 3),
        "throughput_rps": round(len(observations) / duration_s, 3),
        "latency_ms": {
            "min": round(min(latencies), 3) if latencies else 0.0,
            "mean": round(statistics.fmean(latencies), 3) if latencies else 0.0,
            "p50": round(percentile(latencies, 0.50), 3),
            "p95": round(percentile(latencies, 0.95), 3),
            "p99": round(percentile(latencies, 0.99), 3),
            "max": round(max(latencies), 3) if latencies else 0.0,
        },
        "outcomes": dict(sorted(outcomes.items())),
    }
    return metrics, health_samples


def execute_rate_limit_probe(
    *,
    url: str,
    headers: dict[str, str],
    requests: int,
    workers: int,
) -> dict[str, Any]:
    started = time.perf_counter()
    observations: list[tuple[bool, float, int | str]] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(request_once, url, headers) for _ in range(requests)]
        for future in concurrent.futures.as_completed(futures):
            observations.append(future.result())
    duration_s = max(time.perf_counter() - started, 0.000001)
    outcomes: dict[str, int] = {}
    for _, _, outcome in observations:
        key = str(outcome)
        outcomes[key] = outcomes.get(key, 0) + 1
    return {
        "requests": len(observations),
        "workers": workers,
        "duration_seconds": round(duration_s, 3),
        "outcomes": dict(sorted(outcomes.items())),
        "rate_limited_responses": outcomes.get("429", 0),
        "successful_responses": outcomes.get("200", 0),
    }


def write_report(
    path: Path,
    *,
    checks: dict[str, str],
    status: str,
    profiles: dict[str, dict[str, Any]],
    rate_limit_probe: dict[str, Any],
    guardrails: dict[str, Any],
    health_during_load: dict[str, Any] | None,
    baseline: dict[str, Any],
    final_state: dict[str, Any],
    error: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_capacity_claim": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "guardrails": guardrails,
        "profiles": profiles,
        "rate_limit_probe": rate_limit_probe,
        "health_during_load": health_during_load,
        "baseline": baseline,
        "final_state": final_state,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        payload["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--contract",
        default="build-evidence/runtime/performance-load-runtime-contract.json",
    )
    parser.add_argument(
        "--output",
        default="build-evidence/runtime/performance-load-runtime-report.json",
    )
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    output_path = ROOT / args.output
    required_checks = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}
    profiles: dict[str, dict[str, Any]] = {}
    rate_limit_probe: dict[str, Any] = {}
    health_during_load: dict[str, Any] | None = None
    baseline: dict[str, Any] = {}
    final_state: dict[str, Any] = {}
    guardrails = {
        "max_error_rate": float(contract["guardrails"]["max_error_rate"]),
        "max_p95_latency_ms": float(contract["guardrails"]["max_p95_latency_ms"]),
        "min_throughput_rps": float(contract["guardrails"]["min_throughput_rps"]),
    }

    def passed(check_id: str) -> None:
        if check_id not in required_checks:
            raise AssertionError(f"script emitted undeclared check: {check_id}")
        checks[check_id] = "PASS"

    try:
        for service, port, check_id in [
            ("gateway", 8080, "service.gateway.health"),
            ("identity-service", 8082, "service.identity.health"),
            ("user-service", 8081, "service.user.health"),
        ]:
            wait_health(service, port)
            passed(check_id)

        password = "PerformanceProofPass123!"
        # Keep principals independent so the load proof measures aggregate gateway/service
        # behavior without invalidly bypassing or exhausting the per-user read limiter.
        warmup_headers = register_principal("performance-warmup@aetheris.local", password)
        burst_headers = register_principal_pool(
            "burst",
            int(contract["profiles"]["burst"]["principals"]),
            password,
        )
        sustained_headers = register_principal_pool(
            "sustained",
            int(contract["profiles"]["sustained"]["principals"]),
            password,
        )
        overload_headers = register_principal("performance-overload@aetheris.local", password)
        passed("load.authenticated-session-created")

        target = "http://127.0.0.1:8080/api/users"
        status, users = http_json("GET", target, headers=warmup_headers)
        if status != 200 or not isinstance(users, list):
            raise AssertionError(f"baseline protected user read failed: HTTP {status} {users!r}")
        baseline["user_count"] = len(users)
        baseline["warmup_identity_present"] = any(
            isinstance(item, dict) and item.get("email") == "performance-warmup@aetheris.local"
            for item in users
        )

        for _ in range(4):
            status, body = http_json("GET", target, headers=warmup_headers)
            if status != 200 or not isinstance(body, list):
                raise AssertionError(f"warmup failed: HTTP {status} {body!r}")
        cache_keys = run_command(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--scan", "--pattern", "usersList::*"]
        )
        if "usersList::" not in cache_keys:
            raise AssertionError(f"warmup did not materialize usersList cache: {cache_keys!r}")
        passed("load.warmup-completes")

        burst, _ = execute_profile(
            "burst",
            url=target,
            headers_pool=burst_headers,
            total_requests=int(contract["profiles"]["burst"]["requests"]),
            workers=int(contract["profiles"]["burst"]["concurrency"]),
        )
        sustained, health_during_load = execute_profile(
            "sustained",
            url=target,
            headers_pool=sustained_headers,
            total_requests=int(contract["profiles"]["sustained"]["requests"]),
            workers=int(contract["profiles"]["sustained"]["concurrency"]),
            health_monitor=True,
        )
        profiles = {"burst": burst, "sustained": sustained}

        expected_burst = int(contract["profiles"]["burst"]["requests"])
        expected_sustained = int(contract["profiles"]["sustained"]["requests"])
        if burst["total_requests"] != expected_burst or sustained["total_requests"] != expected_sustained:
            raise AssertionError(f"incomplete load profile observations: {profiles!r}")
        passed("load.concurrent-requests-complete")
        passed("load.sustained-profile-completes")

        if any(profile["error_rate"] > guardrails["max_error_rate"] for profile in profiles.values()):
            raise AssertionError(f"hosted error-rate guardrail exceeded: {profiles!r}")
        passed("load.error-rate-within-hosted-guardrail")

        if any(
            profile["latency_ms"]["p95"] > guardrails["max_p95_latency_ms"]
            for profile in profiles.values()
        ):
            raise AssertionError(f"hosted p95 latency guardrail exceeded: {profiles!r}")
        passed("load.p95-within-hosted-guardrail")

        if any(
            profile["throughput_rps"] < guardrails["min_throughput_rps"]
            for profile in profiles.values()
        ):
            raise AssertionError(f"hosted throughput floor missed: {profiles!r}")
        passed("load.throughput-above-hosted-floor")

        for profile in profiles.values():
            latency = profile["latency_ms"]
            if not all(key in latency for key in ("p50", "p95", "p99", "max")):
                raise AssertionError(f"missing latency percentile evidence: {profile!r}")
            if not (latency["p50"] <= latency["p95"] <= latency["p99"] <= latency["max"]):
                raise AssertionError(f"invalid latency percentile ordering: {profile!r}")
        passed("load.latency-percentiles-recorded")

        if (
            not health_during_load
            or health_during_load["samples"] < 1
            or health_during_load["failures"] != 0
        ):
            raise AssertionError(f"gateway health failed during sustained load: {health_during_load!r}")
        passed("load.gateway-responsive-during-load")

        probe_config = contract["rate_limit_probe"]
        rate_limit_probe = execute_rate_limit_probe(
            url=target,
            headers=overload_headers,
            requests=int(probe_config["requests"]),
            workers=int(probe_config["concurrency"]),
        )
        if rate_limit_probe["rate_limited_responses"] < int(probe_config["min_429_responses"]):
            raise AssertionError(f"single-principal overload did not trigger expected 429s: {rate_limit_probe!r}")
        if rate_limit_probe["successful_responses"] < 1:
            raise AssertionError(f"rate-limit probe never admitted an initial request: {rate_limit_probe!r}")
        passed("load.rate-limit-enforced-under-overload")

        for service, port in [
            ("gateway", 8080),
            ("identity-service", 8082),
            ("user-service", 8081),
        ]:
            wait_health(service, port, timeout=60)
        passed("load.services-healthy-after-load")

        status, final_users = http_json("GET", target, headers=warmup_headers)
        if status != 200 or not isinstance(final_users, list):
            raise AssertionError(f"post-load protected read failed: HTTP {status} {final_users!r}")
        passed("load.protected-read-recovers")

        final_state["user_count"] = len(final_users)
        final_state["warmup_identity_present"] = any(
            isinstance(item, dict) and item.get("email") == "performance-warmup@aetheris.local"
            for item in final_users
        )
        if final_state != baseline:
            raise AssertionError(
                f"protected read state changed across read-only load: baseline={baseline!r}, "
                f"final={final_state!r}"
            )
        passed("load.state-integrity-preserved")

        if contract.get("evidence_class") != "HOSTED_RUNTIME" or contract.get("physical_pc_validation") is not False:
            raise AssertionError("contract truth boundary is not hosted-runtime-only")
        if contract.get("production_capacity_claim") is not False:
            raise AssertionError("contract must explicitly reject production-capacity claims")
        passed("load.truth-boundary")

        missing = sorted(set(required_checks) - set(checks))
        extra = sorted(set(checks) - set(required_checks))
        if missing or extra:
            raise AssertionError(f"performance contract mismatch missing={missing} extra={extra}")

        write_report(
            output_path,
            checks=checks,
            status="PASS",
            profiles=profiles,
            rate_limit_probe=rate_limit_probe,
            guardrails=guardrails,
            health_during_load=health_during_load,
            baseline=baseline,
            final_state=final_state,
        )
        print(
            json.dumps(
                {
                    "status": "PASS",
                    "checks": len(checks),
                    "profiles": profiles,
                    "rate_limit_probe": rate_limit_probe,
                },
                sort_keys=True,
            )
        )
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(
            output_path,
            checks=checks,
            status="FAIL",
            profiles=profiles,
            rate_limit_probe=rate_limit_probe,
            guardrails=guardrails,
            health_during_load=health_during_load,
            baseline=baseline,
            final_state=final_state,
            error=str(exc),
        )
        raise


if __name__ == "__main__":
    raise SystemExit(main())
