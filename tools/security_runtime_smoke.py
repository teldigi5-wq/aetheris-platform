#!/usr/bin/env python3
"""Hosted Docker Compose security proof for the Aetheris core platform.

The harness exercises authentication/authorization attack paths through the real
gateway and inspects PostgreSQL only for one synthetic proof account. Raw bearer
credentials are never written to the generated evidence report.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME security evidence proves only the checked behavior in the "
    "GitHub-hosted Docker Compose environment. It is not a penetration test, "
    "formal security audit/certification, production-secret/KMS validation, "
    "load/DoS testing, Kubernetes security validation, or physical-PC validation."
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


def psql(sql: str) -> str:
    return run_command([
        "docker", "compose", "exec", "-T", "postgres",
        "psql", "-U", "aetheris", "-d", "aetheris", "-tA", "-c", sql,
    ])


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


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def tamper_jwt(token: str) -> str:
    parts = token.split(".")
    if len(parts) != 3 or not parts[2]:
        raise AssertionError("issued access token was not a three-part signed JWT")
    replacement = "A" if parts[2][0] != "A" else "B"
    parts[2] = replacement + parts[2][1:]
    return ".".join(parts)


def refresh_rows(email: str) -> dict[str, bool]:
    query = (
        "select r.token_hash || '|' || r.revoked "
        "from identity_refresh_tokens r join identity_accounts a on a.id=r.account_id "
        f"where a.email={sql_literal(email)} order by r.id;"
    )
    rows: dict[str, bool] = {}
    raw = psql(query)
    if not raw:
        return rows
    for line in raw.splitlines():
        token_hash, revoked = line.strip().split("|", 1)
        rows[token_hash] = revoked.lower() in {"t", "true"}
    return rows


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "security",
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
    parser.add_argument("--contract", default="build-evidence/runtime/security-runtime-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/security-runtime-report.json")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required_checks = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}
    output_path = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in required_checks:
            raise AssertionError(f"script emitted undeclared security check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health("gateway", 8080)
        passed("service.gateway.health")
        wait_health("identity-service", 8082)
        passed("service.identity.health")

        stamp = int(time.time())
        email = f"security-proof-{stamp}@aetheris.local"
        password = "SecurityProofPass123!"
        status, registered = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Security Proof", "email": email, "password": password},
        )
        expect_status(status, 201, "security proof registration")
        if not isinstance(registered, dict):
            raise AssertionError(f"registration response must be JSON: {registered!r}")
        access_token = registered.get("accessToken")
        refresh_token = registered.get("refreshToken")
        account = registered.get("account")
        if not isinstance(access_token, str) or not access_token:
            raise AssertionError("registration response missing accessToken")
        if not isinstance(refresh_token, str) or not refresh_token:
            raise AssertionError("registration response missing refreshToken")
        if not isinstance(account, dict) or account.get("role") != "API_CONSUMER":
            raise AssertionError(f"unexpected registered account context: {account!r}")
        passed("security.register-user")

        status, _ = http_json("GET", "http://127.0.0.1:8080/api/users")
        expect_status(status, 401, "anonymous protected route")
        passed("security.protected-route-rejects-anonymous")

        spoof_headers = {
            "X-Aetheris-User-Id": "1",
            "X-Aetheris-User-Email": "admin@forged.invalid",
            "X-Aetheris-User-Role": "ADMIN",
            "X-Aetheris-Scopes": "users:read users:write services:read orchestrator:write",
        }
        status, _ = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=spoof_headers
        )
        expect_status(status, 401, "forged identity headers without bearer token")
        passed("security.forged-identity-headers-rejected")

        auth_headers = {"Authorization": f"Bearer {access_token}"}
        status, users = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=auth_headers
        )
        expect_status(status, 200, "authorized API_CONSUMER read")
        if not isinstance(users, list):
            raise AssertionError(f"authorized users read must return a list: {users!r}")
        passed("security.valid-consumer-read")

        escalated_headers = {**spoof_headers, "Authorization": f"Bearer {access_token}"}
        status, body = http_json(
            "POST",
            "http://127.0.0.1:8080/api/users",
            {"name": "Should Not Exist", "email": f"forged-{stamp}@aetheris.local"},
            headers=escalated_headers,
        )
        expect_status(status, 403, "forged ADMIN/scopes headers with consumer token")
        if not isinstance(body, dict) or "users:write" not in str(body.get("message", "")):
            raise AssertionError(f"scope-denial response did not identify users:write: {body!r}")
        passed("security.forged-admin-header-cannot-escalate")

        status, _ = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers={"Authorization": f"Bearer {tamper_jwt(access_token)}"},
        )
        expect_status(status, 401, "tampered access token")
        passed("security.tampered-access-token-rejected")

        status, _ = http_json(
            "GET",
            "http://127.0.0.1:8080/api/users",
            headers={"Authorization": f"Bearer {refresh_token}"},
        )
        expect_status(status, 401, "refresh token used as access bearer")
        passed("security.refresh-token-cannot-authenticate")

        password_hash = psql(
            "select password_hash from identity_accounts "
            f"where email={sql_literal(email)};"
        ).strip()
        bcrypt_pattern = re.compile(r"^\$2[aby]\$12\$[./A-Za-z0-9]{53}$")
        if password_hash == password or not bcrypt_pattern.fullmatch(password_hash):
            raise AssertionError("persisted password was not a BCrypt cost-12 hash")
        passed("security.password-stored-as-bcrypt")

        old_digest = hashlib.sha256(refresh_token.encode("utf-8")).hexdigest()
        before_rows = refresh_rows(email)
        if refresh_token in before_rows:
            raise AssertionError("raw refresh token appeared in persisted token values")
        passed("security.refresh-token-not-stored-raw")
        if before_rows.get(old_digest) is not False:
            raise AssertionError(
                f"issued refresh SHA-256 hash was not persisted active: rows={before_rows!r}"
            )
        if not all(re.fullmatch(r"[0-9a-f]{64}", value) for value in before_rows):
            raise AssertionError(f"persisted refresh values were not SHA-256 hex: {before_rows!r}")
        passed("security.refresh-token-stored-as-sha256")

        status, rotated = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": refresh_token},
        )
        expect_status(status, 200, "refresh rotation")
        if not isinstance(rotated, dict) or not isinstance(rotated.get("refreshToken"), str):
            raise AssertionError(f"rotation response missing refresh token: {rotated!r}")
        replacement = rotated["refreshToken"]
        if replacement == refresh_token:
            raise AssertionError("refresh rotation returned the original bearer token")
        passed("security.refresh-rotation-issues-new-token")

        new_digest = hashlib.sha256(replacement.encode("utf-8")).hexdigest()
        after_rows = refresh_rows(email)
        if after_rows.get(old_digest) is not True:
            raise AssertionError(f"rotated token hash was not revoked: rows={after_rows!r}")
        passed("security.rotated-token-revokes-old-hash")
        if new_digest == old_digest or after_rows.get(new_digest) is not False:
            raise AssertionError(f"replacement token hash was not distinct and active: rows={after_rows!r}")
        if replacement in after_rows or refresh_token in after_rows:
            raise AssertionError("a raw refresh credential appeared in persisted token values after rotation")
        passed("security.replacement-token-hash-active")

        status, _ = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": refresh_token},
        )
        expect_status(status, 401, "rotated refresh replay")
        passed("security.rotated-token-replay-rejected")

        missing = sorted(set(required_checks) - set(checks))
        extra = sorted(set(checks) - set(required_checks))
        if missing or extra:
            raise AssertionError(f"security contract mismatch missing={missing} extra={extra}")

        write_report(output_path, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001 - top-level evidence boundary
        write_report(output_path, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
