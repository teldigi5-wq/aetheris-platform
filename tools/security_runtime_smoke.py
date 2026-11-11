#!/usr/bin/env python3
"""Hosted Docker Compose security proof for the Aetheris core platform.

The harness exercises authentication/authorization attack paths through the real
gateway and inspects PostgreSQL only for one synthetic proof account. Raw bearer
credentials are never written to the generated evidence report.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import re
import secrets
import subprocess
import threading
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


def concurrent_refresh(raw_token: str, contenders: int = 8) -> list[tuple[int, Any]]:
    barrier = threading.Barrier(contenders)

    def attempt() -> tuple[int, Any]:
        barrier.wait(timeout=10)
        return http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/refresh",
            {"refreshToken": raw_token},
            timeout=20,
        )

    with ThreadPoolExecutor(max_workers=contenders) as pool:
        futures = [pool.submit(attempt) for _ in range(contenders)]
        return [future.result(timeout=30) for future in futures]


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
        status, _ = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=auth_headers
        )
        expect_status(status, 403, "API_CONSUMER global User read must be denied")
        passed("security.consumer-global-read-denied")

        status, _ = http_json(
            "GET", "http://127.0.0.1:8081/api/users", headers=auth_headers
        )
        expect_status(status, 403, "direct backend must reject API_CONSUMER global User read")
        passed("security.direct-consumer-global-read-denied")

        # CI-only, isolated account: promote a second synthetic principal in the
        # disposable database, then obtain an actual Identity-issued ADMIN JWT.
        # Never forge role claims or use a bypass header for the positive proof.
        admin_email = f"security-proof-admin-{stamp}@aetheris.local"
        admin_password = secrets.token_urlsafe(24) + "Aa1!"
        status, _ = http_json(
            "POST", "http://127.0.0.1:8080/api/auth/register",
            {"name": "Synthetic Security Administrator", "email": admin_email, "password": admin_password},
        )
        expect_status(status, 201, "synthetic administrator account registration")
        promoted_count = psql(
            "with promoted as (update identity_accounts set role='ADMIN' "
            f"where email={sql_literal(admin_email)} and role='API_CONSUMER' returning id) "
            "select count(*) from promoted;"
        ).strip()
        if promoted_count != "1":
            raise AssertionError("disposable synthetic administrator promotion did not affect exactly one account")
        status, admin_login = http_json(
            "POST", "http://127.0.0.1:8080/api/auth/login",
            {"email": admin_email, "password": admin_password},
        )
        expect_status(status, 200, "synthetic administrator Identity login")
        if not isinstance(admin_login, dict) or not isinstance(admin_login.get("accessToken"), str) or (
            not isinstance(admin_login.get("account"), dict)
            or admin_login["account"].get("role") != "ADMIN"
        ):
            raise AssertionError("login did not issue an Identity-authenticated ADMIN account context")
        admin_headers = {"Authorization": f"Bearer {admin_login['accessToken']}"}
        status, users = http_json(
            "GET", "http://127.0.0.1:8080/api/users", headers=admin_headers
        )
        expect_status(status, 200, "signed ADMIN global User read through Gateway")
        if not isinstance(users, list):
            raise AssertionError("authorized administrator User response was not a list")
        passed("security.admin-global-read-allowed")

        status, direct_users = http_json(
            "GET", "http://127.0.0.1:8081/api/users", headers=admin_headers
        )
        expect_status(status, 200, "signed ADMIN direct User service read")
        status, direct_events = http_json(
            "GET", "http://127.0.0.1:8083/api/events", headers=admin_headers
        )
        expect_status(status, 200, "signed ADMIN direct Audit service read")
        if not isinstance(direct_users, list) or not isinstance(direct_events, list):
            raise AssertionError("direct administrator service responses were not lists")
        passed("security.direct-service-admin-read-allowed")

        escalated_headers = {**spoof_headers, "Authorization": f"Bearer {access_token}"}
        status, body = http_json(
            "POST",
            "http://127.0.0.1:8080/api/users",
            {"name": "Should Not Exist", "email": f"forged-{stamp}@aetheris.local"},
            headers=escalated_headers,
        )
        expect_status(status, 403, "forged ADMIN/scopes headers with consumer token")
        if not isinstance(body, dict) or body.get("error") != "Forbidden":
            raise AssertionError("forged ADMIN headers did not produce a gateway role denial")
        passed("security.forged-admin-header-cannot-escalate")

        # Credential route: one peer IP shares one bucket no matter what identity
        # header it sends. Rotating X-Aetheris-User-Id must not mint fresh buckets.
        login_statuses = []
        for attempt in range(14):
            status, _ = http_json(
                "POST",
                "http://127.0.0.1:8080/api/auth/login",
                {"email": f"nobody-{stamp}@aetheris.local", "password": "WrongPassword123!"},
                headers={"X-Aetheris-User-Id": f"rotating-spoof-{attempt}"},
            )
            login_statuses.append(status)
        if 429 not in login_statuses or set(login_statuses) - {401, 429}:
            raise AssertionError(f"spoofed-header login burst was not rate limited: {login_statuses!r}")
        passed("security.auth-rate-limit-ignores-spoofed-identity")
        # Let the per-IP credential bucket (1/s, burst 10) refill for later proofs.
        time.sleep(12)

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

        race_results = concurrent_refresh(replacement, contenders=8)
        race_statuses = [status for status, _ in race_results]
        if race_statuses.count(200) != 1 or race_statuses.count(401) != 7:
            raise AssertionError(
                "concurrent refresh must have exactly one winner and seven rejected replays: "
                f"statuses={sorted(race_statuses)!r}"
            )
        successful_bodies = [body for status, body in race_results if status == 200]
        winner_body = successful_bodies[0]
        if not isinstance(winner_body, dict) or not isinstance(winner_body.get("refreshToken"), str):
            raise AssertionError(f"concurrent refresh winner missing replacement token: {winner_body!r}")
        concurrent_replacement = winner_body["refreshToken"]
        if concurrent_replacement == replacement:
            raise AssertionError("concurrent refresh winner returned the consumed token")
        passed("security.concurrent-refresh-single-winner")

        concurrent_digest = hashlib.sha256(concurrent_replacement.encode("utf-8")).hexdigest()
        race_rows = refresh_rows(email)
        if race_rows.get(new_digest) is not True:
            raise AssertionError(f"concurrently consumed token hash was not revoked: rows={race_rows!r}")
        if race_rows.get(concurrent_digest) is not False:
            raise AssertionError(f"winning descendant token hash is not active: rows={race_rows!r}")
        active_hashes = [token_hash for token_hash, revoked in race_rows.items() if not revoked]
        if active_hashes != [concurrent_digest]:
            raise AssertionError(
                "concurrent rotation created more than one active descendant refresh credential: "
                f"active_hashes={active_hashes!r}"
            )
        passed("security.concurrent-refresh-single-active-descendant")

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
