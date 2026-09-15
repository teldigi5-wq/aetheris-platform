#!/usr/bin/env python3
"""Hosted Docker Compose data durability and recovery proof for Aetheris.

This harness creates synthetic identity and user data, verifies normal restart
survival and Redis reconstruction, creates a PostgreSQL custom-format backup,
restores that backup into a fresh isolated database, and launches temporary
service instances against the restored copy to prove the recovered data is
usable. The raw backup and raw credentials stay inside the ephemeral CI runtime.
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
BACKUP_PATH = "/tmp/aetheris-data-recovery.dump"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME data-recovery evidence proves only the checked Docker Compose "
    "restart, cache-reconstruction, backup, isolated restore, and recovered-service "
    "behavior on a GitHub-hosted runner. It is not production disaster-recovery "
    "certification, PITR/WAL-archive validation, encrypted/off-site backup validation, "
    "cross-region recovery, an RPO/RTO guarantee, Kubernetes PVC/CSI recovery "
    "validation, or physical-PC validation."
)


def run_command(args: list[str], timeout: int = 180) -> str:
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


def command_succeeds(args: list[str], timeout: int = 30) -> bool:
    try:
        result = subprocess.run(
            args,
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=timeout,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0


def psql(sql: str, database: str = "aetheris") -> str:
    return run_command([
        "docker", "compose", "exec", "-T", "postgres",
        "psql", "-U", "aetheris", "-d", database,
        "-tA", "-v", "ON_ERROR_STOP=1", "-c", sql,
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


def wait_postgres(timeout: int = 120) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if command_succeeds([
            "docker", "compose", "exec", "-T", "postgres",
            "pg_isready", "-U", "aetheris", "-d", "aetheris",
        ]):
            return
        time.sleep(2)
    raise AssertionError("PostgreSQL did not become ready")


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def identity_snapshot(email: str, database: str = "aetheris") -> str:
    return psql(
        "select id || '|' || name || '|' || email || '|' || password_hash || '|' || role "
        "from identity_accounts "
        f"where email={sql_literal(email)};",
        database,
    ).strip()


def user_snapshot(email: str, database: str = "aetheris") -> str:
    return psql(
        "select id || '|' || name || '|' || email from users "
        f"where email={sql_literal(email)};",
        database,
    ).strip()


def refresh_snapshot(email: str, database: str = "aetheris") -> str:
    return psql(
        "select r.id || '|' || r.account_id || '|' || r.token_hash || '|' || r.revoked || '|' || "
        "extract(epoch from r.expires_at)::bigint "
        "from identity_refresh_tokens r join identity_accounts a on a.id=r.account_id "
        f"where a.email={sql_literal(email)} order by r.id;",
        database,
    ).strip()


def redis_user_cache_keys() -> list[str]:
    raw = run_command([
        "docker", "compose", "exec", "-T", "redis",
        "redis-cli", "--raw", "--scan", "--pattern", "usersList*",
    ])
    return sorted(line.strip() for line in raw.splitlines() if line.strip())


def protected_users(access_token: str) -> list[dict[str, Any]]:
    status, body = http_json(
        "GET",
        "http://127.0.0.1:8080/api/users",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    expect_status(status, 200, "protected user list")
    if not isinstance(body, list):
        raise AssertionError(f"protected users response must be a list: {body!r}")
    return body


def assert_user_visible(users: list[dict[str, Any]], user_id: int, email: str) -> None:
    if not any(
        isinstance(item, dict)
        and item.get("id") == user_id
        and item.get("email") == email
        for item in users
    ):
        raise AssertionError(f"synthetic user {user_id} was not visible in user list")


def stop_temporary_container(name: str | None) -> None:
    if not name:
        return
    subprocess.run(
        ["docker", "stop", name],
        cwd=ROOT,
        text=True,
        capture_output=True,
        timeout=30,
        check=False,
    )


def write_report(
    path: Path,
    checks: dict[str, str],
    status: str,
    backup_digest: str | None = None,
    backup_bytes: int | None = None,
    error: str | None = None,
) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "data-recovery",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_disaster_recovery_certification": False,
        "rpo_rto_certification": False,
        "raw_backup_published": False,
        "restore_mode": "fresh-isolated-database",
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if backup_digest is not None and backup_bytes is not None:
        payload["backup"] = {
            "format": "postgresql-custom",
            "sha256": backup_digest,
            "bytes": backup_bytes,
            "published": False,
        }
    if error:
        payload["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--contract",
        default="build-evidence/runtime/data-recovery-runtime-contract.json",
    )
    parser.add_argument(
        "--output",
        default="build-evidence/runtime/data-recovery-runtime-report.json",
    )
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required_checks = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}
    output_path = ROOT / args.output
    backup_digest: str | None = None
    backup_bytes: int | None = None
    recovery_identity_container: str | None = None
    recovery_user_container: str | None = None

    def passed(check_id: str) -> None:
        if check_id not in required_checks:
            raise AssertionError(f"script emitted undeclared data-recovery check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health("gateway", 8080)
        passed("service.gateway.health")
        wait_health("identity-service", 8082)
        passed("service.identity.health")
        wait_health("user-service", 8081)
        passed("service.user.health")

        stamp = int(time.time())
        identity_email = f"recovery-identity-{stamp}@aetheris.local"
        user_email = f"recovery-user-{stamp}@aetheris.local"
        password = "RecoveryProofPass123!"
        recovery_database = f"aetheris_recovery_{stamp}"

        status, registered = http_json(
            "POST",
            "http://127.0.0.1:8080/api/auth/register",
            {"name": "Recovery Identity", "email": identity_email, "password": password},
        )
        expect_status(status, 201, "recovery proof registration")
        if not isinstance(registered, dict):
            raise AssertionError(f"registration response must be JSON: {registered!r}")
        access_token = registered.get("accessToken")
        refresh_token = registered.get("refreshToken")
        if not isinstance(access_token, str) or not access_token:
            raise AssertionError("registration response missing accessToken")
        if not isinstance(refresh_token, str) or not refresh_token:
            raise AssertionError("registration response missing refreshToken")

        status, created_user = http_json(
            "POST",
            "http://127.0.0.1:8081/api/users",
            {"name": "Recovery User", "email": user_email},
        )
        expect_status(status, 201, "synthetic user creation")
        if not isinstance(created_user, dict) or not isinstance(created_user.get("id"), int):
            raise AssertionError(f"user creation response missing numeric id: {created_user!r}")
        user_id = created_user["id"]

        baseline_identity = identity_snapshot(identity_email)
        if not baseline_identity:
            raise AssertionError("synthetic identity row was not persisted")
        passed("data.identity-baseline-persisted")

        baseline_user = user_snapshot(user_email)
        if not baseline_user:
            raise AssertionError("synthetic user row was not persisted")
        passed("data.user-baseline-persisted")

        baseline_refresh = refresh_snapshot(identity_email)
        expected_digest = hashlib.sha256(refresh_token.encode("utf-8")).hexdigest()
        if not baseline_refresh or expected_digest not in baseline_refresh:
            raise AssertionError("synthetic refresh-token hash metadata was not persisted")
        if refresh_token in baseline_refresh or not re.search(r"[0-9a-f]{64}", baseline_refresh):
            raise AssertionError("refresh-token persistence shape was not hash-only")
        passed("data.refresh-baseline-persisted")

        users = protected_users(access_token)
        assert_user_visible(users, user_id, user_email)

        run_command(["docker", "compose", "restart", "postgres"], timeout=120)
        wait_postgres()
        wait_health("identity-service after postgres restart", 8082)
        wait_health("user-service after postgres restart", 8081)
        if identity_snapshot(identity_email) != baseline_identity:
            raise AssertionError("identity row changed across PostgreSQL restart")
        if user_snapshot(user_email) != baseline_user:
            raise AssertionError("user row changed across PostgreSQL restart")
        if refresh_snapshot(identity_email) != baseline_refresh:
            raise AssertionError("refresh metadata changed across PostgreSQL restart")
        users = protected_users(access_token)
        assert_user_visible(users, user_id, user_email)
        passed("durability.postgres-restart-survives")

        run_command([
            "docker", "compose", "restart", "identity-service", "user-service", "gateway"
        ], timeout=180)
        wait_health("identity-service after service restart", 8082)
        wait_health("user-service after service restart", 8081)
        wait_health("gateway after service restart", 8080)
        users = protected_users(access_token)
        assert_user_visible(users, user_id, user_email)
        passed("durability.service-restart-survives")

        if not redis_user_cache_keys():
            raise AssertionError("usersList cache key was not populated after protected read")
        passed("cache.users-list-populated")

        flush_result = run_command([
            "docker", "compose", "exec", "-T", "redis", "redis-cli", "FLUSHALL"
        ])
        if "OK" not in flush_result:
            raise AssertionError(f"Redis FLUSHALL did not return OK: {flush_result!r}")
        if redis_user_cache_keys():
            raise AssertionError("usersList cache remained after Redis FLUSHALL")
        passed("cache.redis-cleared")

        users = protected_users(access_token)
        assert_user_visible(users, user_id, user_email)
        passed("cache.authoritative-read-recovers")
        if not redis_user_cache_keys():
            raise AssertionError("usersList cache was not reconstructed after authoritative read")
        passed("cache.users-list-repopulated")

        run_command([
            "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
            f"pg_dump -U aetheris -d aetheris -Fc -f {BACKUP_PATH} && test -s {BACKUP_PATH}",
        ], timeout=180)
        backup_bytes = int(run_command([
            "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
            f"wc -c < {BACKUP_PATH}",
        ]).strip())
        if backup_bytes <= 0:
            raise AssertionError("PostgreSQL backup was empty")
        passed("backup.postgres-created")

        backup_digest = run_command([
            "docker", "compose", "exec", "-T", "postgres", "sh", "-lc",
            f"sha256sum {BACKUP_PATH} | cut -d' ' -f1",
        ]).strip()
        if not re.fullmatch(r"[0-9a-f]{64}", backup_digest):
            raise AssertionError(f"invalid backup SHA-256 digest: {backup_digest!r}")
        passed("backup.checksum-recorded")

        run_command([
            "docker", "compose", "exec", "-T", "postgres",
            "createdb", "-U", "aetheris", recovery_database,
        ])
        passed("recovery.fresh-database-created")

        run_command([
            "docker", "compose", "exec", "-T", "postgres",
            "pg_restore", "-U", "aetheris", "-d", recovery_database,
            "--exit-on-error", BACKUP_PATH,
        ], timeout=180)
        passed("recovery.restore-completes")

        if identity_snapshot(identity_email, recovery_database) != baseline_identity:
            raise AssertionError("recovered identity row did not exactly match baseline")
        passed("recovery.identity-restored")
        if user_snapshot(user_email, recovery_database) != baseline_user:
            raise AssertionError("recovered user row did not exactly match baseline")
        passed("recovery.user-restored")
        if refresh_snapshot(identity_email, recovery_database) != baseline_refresh:
            raise AssertionError("recovered refresh metadata did not exactly match baseline")
        passed("recovery.refresh-metadata-restored")

        recovery_identity_container = f"aetheris-recovery-identity-{stamp}"
        run_command([
            "docker", "compose", "run", "-d", "--rm", "--no-deps",
            "--name", recovery_identity_container,
            "-e", "SERVER_PORT=18082",
            "-e", f"SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/{recovery_database}",
            "-p", "18082:18082", "identity-service",
        ], timeout=120)
        wait_health("recovered identity-service", 18082)

        status, recovered_login = http_json(
            "POST",
            "http://127.0.0.1:18082/api/auth/login",
            {"email": identity_email, "password": password},
        )
        expect_status(status, 200, "login against recovered identity database")
        if not isinstance(recovered_login, dict) or not isinstance(recovered_login.get("accessToken"), str):
            raise AssertionError(f"recovered login missing access token: {recovered_login!r}")
        passed("recovery.restored-identity-authenticates")

        status, recovered_refresh = http_json(
            "POST",
            "http://127.0.0.1:18082/api/auth/refresh",
            {"refreshToken": refresh_token},
        )
        expect_status(status, 200, "pre-backup refresh token against recovered database")
        if not isinstance(recovered_refresh, dict) or not isinstance(recovered_refresh.get("accessToken"), str):
            raise AssertionError(f"recovered refresh response missing access token: {recovered_refresh!r}")
        passed("recovery.restored-refresh-token-operational")

        run_command([
            "docker", "compose", "exec", "-T", "redis", "redis-cli", "FLUSHALL"
        ])
        recovery_user_container = f"aetheris-recovery-user-{stamp}"
        run_command([
            "docker", "compose", "run", "-d", "--rm", "--no-deps",
            "--name", recovery_user_container,
            "-e", "SERVER_PORT=18081",
            "-e", f"SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/{recovery_database}",
            "-p", "18081:18081", "user-service",
        ], timeout=120)
        wait_health("recovered user-service", 18081)
        status, recovered_users = http_json("GET", "http://127.0.0.1:18081/api/users")
        expect_status(status, 200, "user read against recovered database")
        if not isinstance(recovered_users, list):
            raise AssertionError(f"recovered users response must be a list: {recovered_users!r}")
        assert_user_visible(recovered_users, user_id, user_email)
        passed("recovery.restored-user-readable")

        users = protected_users(access_token)
        assert_user_visible(users, user_id, user_email)
        passed("recovery.primary-remains-operational")

        missing = sorted(set(required_checks) - set(checks))
        extra = sorted(set(checks) - set(required_checks))
        if missing or extra:
            raise AssertionError(f"data-recovery contract mismatch missing={missing} extra={extra}")

        write_report(output_path, checks, "PASS", backup_digest, backup_bytes)
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001 - top-level evidence boundary
        write_report(
            output_path,
            checks,
            "FAIL",
            backup_digest=backup_digest,
            backup_bytes=backup_bytes,
            error=str(exc),
        )
        raise
    finally:
        stop_temporary_container(recovery_user_container)
        stop_temporary_container(recovery_identity_container)


if __name__ == "__main__":
    raise SystemExit(main())
