#!/usr/bin/env python3
"""Hosted Docker Compose resilience proof for the Aetheris gateway.

This harness deliberately exercises a real downstream outage and verifies the
configured circuit breaker moves CLOSED -> OPEN -> HALF_OPEN -> CLOSED without
restarting the gateway. The evidence is limited to GitHub-hosted Compose.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME resilience evidence proves only the checked behavior in the "
    "GitHub-hosted Docker Compose environment. It is not production readiness, "
    "load/chaos certification, or physical-PC validation."
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


def compose(*args: str, timeout: int = 120) -> str:
    return run_command(["docker", "compose", *args], timeout=timeout)


def http_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 15,
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


def expect_status(actual: int, expected: int, context: str) -> None:
    if actual != expected:
        raise AssertionError(f"{context}: expected HTTP {expected}, got {actual}")


def wait_health(name: str, port: int, timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last: Any = "not attempted"
    url = f"http://127.0.0.1:{port}/actuator/health"
    while time.monotonic() < deadline:
        try:
            status, body = http_json("GET", url, timeout=5)
            last = (status, body)
            if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001 - readiness retry boundary
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"{name} did not become healthy: last={last!r}")


def gateway_control_plane() -> None:
    status, body = http_json("GET", "http://127.0.0.1:8080/actuator/info")
    expect_status(status, 200, "gateway actuator info")
    if not isinstance(body, dict):
        raise AssertionError(f"gateway actuator info was not JSON: {body!r}")


def circuit_snapshot(name: str) -> dict[str, Any]:
    status, body = http_json("GET", "http://127.0.0.1:8080/actuator/circuitbreakers")
    expect_status(status, 200, "circuit breaker actuator")
    if not isinstance(body, dict):
        raise AssertionError(f"unexpected circuit breaker actuator body: {body!r}")

    collection = body.get("circuitBreakers")
    if isinstance(collection, dict):
        snapshot = collection.get(name)
        if isinstance(snapshot, dict):
            return snapshot

    if isinstance(collection, list):
        for item in collection:
            if isinstance(item, dict) and item.get("name") == name:
                return item

    raise AssertionError(f"circuit breaker {name!r} not present: {body!r}")


def circuit_state(name: str) -> str:
    state = circuit_snapshot(name).get("state")
    if not isinstance(state, str):
        raise AssertionError(f"circuit breaker {name!r} missing state")
    return state.upper()


def wait_circuit_state(name: str, desired: set[str], timeout: int = 30) -> str:
    deadline = time.monotonic() + timeout
    last = "UNKNOWN"
    normalized = {state.upper() for state in desired}
    while time.monotonic() < deadline:
        last = circuit_state(name)
        if last in normalized:
            return last
        time.sleep(0.5)
    raise AssertionError(
        f"circuit breaker {name!r} did not reach {sorted(normalized)}; last={last}"
    )


def not_permitted_calls(snapshot: dict[str, Any]) -> float:
    value = snapshot.get("notPermittedCalls", 0)
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        cleaned = value.strip().rstrip("%")
        try:
            return float(cleaned)
        except ValueError as exc:
            raise AssertionError(f"invalid notPermittedCalls value: {value!r}") from exc
    raise AssertionError(f"invalid notPermittedCalls value: {value!r}")


def assert_fallback(status: int, body: Any) -> None:
    expect_status(status, 503, "user-service fallback")
    if not isinstance(body, dict):
        raise AssertionError(f"fallback must be JSON: {body!r}")
    if body.get("service") != "user-service":
        raise AssertionError(f"fallback did not identify user-service: {body!r}")
    if body.get("retryable") is not True:
        raise AssertionError(f"fallback did not mark retryable=true: {body!r}")


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "resilience",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
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
        default="build-evidence/runtime/resilience-runtime-contract.json",
    )
    parser.add_argument(
        "--output",
        default="build-evidence/runtime/resilience-runtime-report.json",
    )
    args = parser.parse_args()

    contract_path = ROOT / args.contract
    output_path = ROOT / args.output
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    required_checks = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}

    def passed(check_id: str) -> None:
        if check_id not in required_checks:
            raise AssertionError(f"script emitted undeclared resilience check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health("gateway", 8080)
        passed("service.gateway.health")
        wait_health("identity-service", 8082)
        passed("service.identity.health")
        wait_health("user-service", 8081)
        passed("service.user.health")

        email = f"resilience-proof-{int(time.time())}@aetheris.local"
        password = "ResilienceProofPass123!"
        status, registered = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Resilience Proof", "email": email, "password": password},
        )
        expect_status(status, 201, "resilience proof registration")
        if not isinstance(registered, dict) or not registered.get("accessToken"):
            raise AssertionError(f"registration response missing access token: {registered!r}")
        headers = {"Authorization": f"Bearer {registered['accessToken']}"}

        status, users = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers=headers,
        )
        expect_status(status, 200, "baseline protected user read")
        if not isinstance(users, list):
            raise AssertionError(f"baseline user read must return a list: {users!r}")
        passed("resilience.read-baseline")

        initial_state = wait_circuit_state("userReadCircuit", {"CLOSED"}, timeout=10)
        if initial_state != "CLOSED":
            raise AssertionError(f"unexpected initial circuit state: {initial_state}")
        passed("resilience.circuit-initially-closed")

        compose("stop", "-t", "10", "user-service", timeout=30)

        observed_fallback = False
        for _ in range(8):
            status, body = http_json(
                "GET",
                "http://127.0.0.1:8080/api/users",
                headers=headers,
                timeout=20,
            )
            if status == 429:
                time.sleep(1.5)
                continue
            assert_fallback(status, body)
            observed_fallback = True
            if circuit_state("userReadCircuit") == "OPEN":
                break
            time.sleep(0.5)

        if not observed_fallback:
            raise AssertionError("no structured fallback was observed during user-service outage")
        passed("resilience.fallback-structured")

        wait_circuit_state("userReadCircuit", {"OPEN"}, timeout=10)
        passed("resilience.user-read-circuit-opens")

        before = not_permitted_calls(circuit_snapshot("userReadCircuit"))
        status, body = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers=headers,
            timeout=10,
        )
        assert_fallback(status, body)
        after = not_permitted_calls(circuit_snapshot("userReadCircuit"))
        if after <= before:
            raise AssertionError(
                f"OPEN circuit did not increment notPermittedCalls: before={before}, after={after}"
            )
        passed("resilience.open-circuit-short-circuits")

        gateway_control_plane()
        passed("resilience.gateway-control-plane-responsive")

        compose("start", "user-service", timeout=30)
        wait_health("user-service", 8081)
        passed("resilience.user-service-restarts")

        wait_circuit_state("userReadCircuit", {"HALF_OPEN"}, timeout=25)
        passed("resilience.user-read-circuit-half-open")

        successful_probes = 0
        for _ in range(3):
            status, body = http_json(
                "GET",
                "http://127.0.0.1:8080/api/users",
                headers=headers,
                timeout=15,
            )
            expect_status(status, 200, "half-open recovery probe")
            if not isinstance(body, list):
                raise AssertionError(f"half-open recovery probe must return a list: {body!r}")
            successful_probes += 1
            time.sleep(0.25)

        if successful_probes != 3:
            raise AssertionError(f"expected 3 successful half-open probes, got {successful_probes}")
        wait_circuit_state("userReadCircuit", {"CLOSED"}, timeout=10)
        passed("resilience.user-read-circuit-closes")

        status, body = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers=headers,
        )
        expect_status(status, 200, "protected user read after recovery")
        if not isinstance(body, list):
            raise AssertionError(f"recovered user read must return a list: {body!r}")
        passed("resilience.protected-read-recovers")

        missing = sorted(set(required_checks) - set(checks))
        extra = sorted(set(checks) - set(required_checks))
        if missing or extra:
            raise AssertionError(f"resilience contract mismatch missing={missing} extra={extra}")

        write_report(output_path, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001 - top-level evidence boundary
        write_report(output_path, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
