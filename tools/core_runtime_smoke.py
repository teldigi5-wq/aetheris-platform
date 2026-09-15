#!/usr/bin/env python3
"""Hosted Docker Compose smoke proof for the Aetheris core platform.

This script intentionally proves only behavior observable in the GitHub-hosted
Docker Compose environment. It is not physical-PC or production validation.
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
    "HOSTED_RUNTIME evidence proves the checked behavior in the GitHub-hosted Docker Compose "
    "environment only. It is not production readiness or physical-PC validation."
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

    request = urllib.request.Request(
        url,
        data=body,
        headers=request_headers,
        method=method,
    )
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
    last: str = "not attempted"
    url = f"http://127.0.0.1:{port}/actuator/health"
    while time.monotonic() < deadline:
        try:
            status, body = http_json("GET", url, timeout=5)
            last = f"HTTP {status} body={body!r}"
            if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001 - readiness retry boundary
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"{name} did not become healthy: {last}")


def wait_for_event(email: str, timeout: int = 30) -> None:
    deadline = time.monotonic() + timeout
    last: Any = None
    while time.monotonic() < deadline:
        status, body = http_json("GET", "http://127.0.0.1:8083/api/events")
        last = body
        if status == 200 and isinstance(body, list):
            if any(
                isinstance(event, dict)
                and event.get("eventType") == "user.created"
                and event.get("email") == email
                for event in body
            ):
                return
        time.sleep(1)
    raise AssertionError(f"RabbitMQ audit event was not observed; last body={last!r}")


def wait_gateway_users(access_token: str, timeout: int = 40) -> None:
    deadline = time.monotonic() + timeout
    headers = {"Authorization": f"Bearer {access_token}"}
    last: Any = None
    while time.monotonic() < deadline:
        status, body = http_json("GET", "http://127.0.0.1:8080/api/users", headers=headers)
        last = (status, body)
        if status == 200:
            return
        time.sleep(2)
    raise AssertionError(f"gateway did not recover user-service route; last={last!r}")


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
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
    parser.add_argument("--contract", default="build-evidence/runtime/core-runtime-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/core-runtime-report.json")
    args = parser.parse_args()

    contract_path = ROOT / args.contract
    output_path = ROOT / args.output
    contract = json.loads(contract_path.read_text(encoding="utf-8"))
    required_checks = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}

    def passed(check_id: str) -> None:
        if check_id not in required_checks:
            raise AssertionError(f"script emitted undeclared runtime check: {check_id}")
        checks[check_id] = "PASS"

    try:
        # Infrastructure readiness from inside the actual Compose containers.
        run_command(["docker", "compose", "exec", "-T", "postgres", "pg_isready", "-U", "aetheris", "-d", "aetheris"])
        passed("infrastructure.postgres.ready")

        redis_ping = run_command(["docker", "compose", "exec", "-T", "redis", "redis-cli", "ping"])
        if "PONG" not in redis_ping:
            raise AssertionError(f"unexpected redis ping output: {redis_ping!r}")
        passed("infrastructure.redis.ready")

        run_command(["docker", "compose", "exec", "-T", "rabbitmq", "rabbitmq-diagnostics", "-q", "ping"])
        passed("infrastructure.rabbitmq.ready")

        # Service readiness.
        for name, port, check_id in [
            ("identity-service", 8082, "service.identity.health"),
            ("user-service", 8081, "service.user.health"),
            ("audit-service", 8083, "service.audit.health"),
            ("gateway", 8080, "service.gateway.health"),
        ]:
            wait_health(name, port)
            passed(check_id)

        # Identity lifecycle through the gateway.
        identity_email = "runtime-proof@aetheris.local"
        password = "RuntimeProofPass123!"
        status, registered = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Runtime Proof", "email": identity_email, "password": password},
        )
        expect_status(status, 201, "register")
        if not isinstance(registered, dict):
            raise AssertionError("register response must be JSON")
        access_token = registered.get("accessToken")
        original_refresh = registered.get("refreshToken")
        if not access_token or not original_refresh:
            raise AssertionError("register response missing access/refresh token")
        passed("auth.register")

        status, _ = http_json("GET", "http://127.0.0.1:8080/api/users")
        expect_status(status, 401, "protected users route without token")
        passed("auth.gateway-rejects-missing-token")

        auth_headers = {"Authorization": f"Bearer {access_token}"}
        status, users = http_json("GET", "http://127.0.0.1:8080/api/users", headers=auth_headers)
        expect_status(status, 200, "authorized users read")
        if not isinstance(users, list):
            raise AssertionError("authorized users read did not return a list")
        passed("auth.gateway-authorized-read")

        status, _ = http_json(
            "POST",
            "http://127.0.0.1:8080/api/users",
            {"name": "Should Be Rejected", "email": "scope-denied@aetheris.local"},
            headers=auth_headers,
        )
        expect_status(status, 403, "API_CONSUMER users:write scope denial")
        passed("auth.gateway-rejects-insufficient-scope")

        status, refreshed = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": original_refresh},
        )
        expect_status(status, 200, "refresh rotation")
        if not isinstance(refreshed, dict):
            raise AssertionError("refresh response must be JSON")
        rotated_refresh = refreshed.get("refreshToken")
        if not rotated_refresh or rotated_refresh == original_refresh:
            raise AssertionError("refresh token was not rotated")
        passed("auth.refresh-rotation")

        status, _ = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": original_refresh},
        )
        expect_status(status, 401, "reused refresh token")
        passed("auth.reused-refresh-rejected")

        status, _ = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/logout",
            {"refreshToken": rotated_refresh},
        )
        expect_status(status, 204, "logout")
        status, _ = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": rotated_refresh},
        )
        expect_status(status, 401, "refresh after logout")
        passed("auth.logout-revokes-refresh")

        # Internal user-service probe for cache + asynchronous event flow.
        runtime_user_email = "runtime-user@aetheris.local"
        status, created_user = http_json(
            "POST",
            "http://127.0.0.1:8081/api/users",
            {"name": "Runtime User", "email": runtime_user_email},
        )
        expect_status(status, 201, "internal user create")
        if not isinstance(created_user, dict) or not created_user.get("id"):
            raise AssertionError("created user response missing id")
        user_id = created_user["id"]

        status, _ = http_json("GET", f"http://127.0.0.1:8081/api/users/{user_id}")
        expect_status(status, 200, "userById cache population")
        cache_keys = run_command(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--scan", "--pattern", "userById::*"]
        )
        if "userById::" not in cache_keys:
            raise AssertionError(f"userById cache key not materialized; keys={cache_keys!r}")
        passed("cache.user-by-id-materialized")

        status, _ = http_json("GET", "http://127.0.0.1:8081/api/users")
        expect_status(status, 200, "usersList cache population")
        list_cache_keys = run_command(
            ["docker", "compose", "exec", "-T", "redis", "redis-cli", "--scan", "--pattern", "usersList::*"]
        )
        if "usersList::" not in list_cache_keys:
            raise AssertionError(f"usersList cache key not materialized; keys={list_cache_keys!r}")
        passed("cache.users-list-materialized")

        wait_for_event(runtime_user_email)
        passed("messaging.user-created-audit-event")

        # Resilience: force user-service unavailable, require gateway fallback, then recover.
        compose("stop", "-t", "10", "user-service", timeout=30)
        status, fallback = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers=auth_headers,
            timeout=20,
        )
        expect_status(status, 503, "gateway fallback while user-service stopped")
        if not isinstance(fallback, dict) or fallback.get("service") != "user-service":
            raise AssertionError(f"unexpected fallback body: {fallback!r}")
        passed("resilience.user-service-fallback")

        compose("start", "user-service", timeout=30)
        wait_health("user-service", 8081)
        wait_gateway_users(access_token)
        passed("resilience.user-service-recovery")

        missing = sorted(set(required_checks) - set(checks))
        extra = sorted(set(checks) - set(required_checks))
        if missing or extra:
            raise AssertionError(f"runtime contract mismatch missing={missing} extra={extra}")

        write_report(output_path, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001 - top-level evidence boundary
        write_report(output_path, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
