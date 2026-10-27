#!/usr/bin/env python3
"""B62-G9 CI ONLY: real PostgreSQL multiple reader process concurrency.

This script NEVER implements a production JWT verifier. Only prevalidated
synthetic tuples are used. The independent 'stale-snapshot' database is
deliberately stale; it is NOT a real streaming-replication PostgreSQL replica.
Fail-closed authorization MUST consult the primary, never the stale snapshot.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
import subprocess

COMPOSE = (
    "docker", "compose",
    "-f", "tests/security/docker-compose.b62g-epoch-proof.yml",
    "-f", "tests/security/b62g9-stale-snapshot-overlay.yml",
)
READERS = {
    "b62g8_gateway_reader": "b62g8-gateway-synthetic-ci-only",
    "b62g8_user_reader": "b62g8-user-synthetic-ci-only",
    "b62g8_audit_reader": "b62g8-audit-synthetic-ci-only",
}
ISSUER, KID = "b62g-identity", "key-current"
TIMEOUT_SECONDS = 8.0


def raw_read(role: str, principal: int, iat: int, *, snapshot: bool = False,
             password_override: str | None = None) -> str:
    """Returns ALLOW, DENY or ERROR, never guesses after a missing response."""
    if (role not in READERS or type(principal) is not int
            or principal not in (42, 43) or type(iat) is not int
            or iat not in (11000, 12001, 14001)):
        return "ERROR"
    service = "stale-snapshot" if snapshot else "postgres"
    password = READERS[role] if password_override is None else password_override
    sql = (
        "SELECT b62g_proof.synthetic_post_signature_allowed("
        f"{principal},'{ISSUER}','{KID}',{iat});"
    )
    args = [
        *COMPOSE, "exec", "-T", "-e", f"PGPASSWORD={password}",
        service, "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
        "-h", "127.0.0.1", "-p", "5432",
        "-U", role, "-d", "b62g_synthetic", "-c", sql,
    ]
    try:
        result = subprocess.run(
            args, capture_output=True, text=True, timeout=TIMEOUT_SECONDS,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return "ERROR"
    if result.returncode != 0:
        return "ERROR"
    return {"t": "ALLOW", "f": "DENY"}.get(result.stdout.strip(), "ERROR")


def primary_authoritative(role: str, uid: int, iat: int) -> str:
    """Never queries stale-snapshot. ERROR is DENY; no cached allow."""
    return "ALLOW" if raw_read(role, uid, iat) == "ALLOW" else "DENY"


def require_primary(role: str, uid: int, iat: int, expected_raw: str) -> None:
    raw = raw_read(role, uid, iat)
    if raw != expected_raw:
        raise AssertionError(
            f"B62G9_PRIMARY_DECISION_MISMATCH role={role} expected={expected_raw} got={raw}"
        )
    expected_decision = "ALLOW" if expected_raw == "ALLOW" else "DENY"
    actual = primary_authoritative(role, uid, iat)
    if actual != expected_decision:
        raise AssertionError(
            f"B62G9_AUTHORITY_BYPASS role={role} expected={expected_decision} got={actual}"
        )


def check_stale(uid: int, iat: int, expected: str) -> None:
    raw = raw_read("b62g8_user_reader", uid, iat, snapshot=True)
    if raw != expected:
        raise AssertionError(f"B62G9_STALE_SNAPSHOT_NOT_PROVEN expected={expected} got={raw}")


def concurrent_primary_check(uid: int, iat: int, expected: str) -> None:
    tasks = [(role, uid, iat, expected) for role in READERS for _ in range(4)]
    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(lambda t: require_primary(*t), tasks))
    print(f"B62G9_CONCURRENT_PRIMARY_READERS_PASS clients={len(READERS)} "
          f"requests={len(tasks)} decision={expected}", flush=True)


def run(stage: str) -> None:
    assert len(READERS) == 3
    if stage == "baseline":
        for role in READERS:
            require_primary(role, 42, 11000, "ALLOW")
            require_primary(role, 43, 14001, "ALLOW")
        check_stale(42, 11000, "ALLOW")
        denied_login = raw_read(
            "b62g8_gateway_reader", 42, 11000,
            password_override="incorrect-synthetic-password",
        )
        if denied_login != "ERROR":
            raise AssertionError("B62G9_INVALID_PASSWORD_ACCEPTED")
        print("B62G9_THREE_PASSWORD_READERS_BASELINE_ALLOW", flush=True)
    elif stage == "postcommit":
        # The stale DB must still explicitly ALLOW the token the primary
        # denies. This makes accidental fallback observable in CI.
        check_stale(42, 11000, "ALLOW")
        concurrent_primary_check(42, 11000, "DENY")
        concurrent_primary_check(43, 14001, "ALLOW")
        for role in READERS:
            require_primary(role, 42, 12001, "ALLOW")
        print("B62G9_STALE_ALLOWS_BUT_AUTHORIZED_PRIMARY_DENIES_PASS", flush=True)
    elif stage in ("blocked", "restarted"):
        check_stale(43, 14001, "ALLOW")
        concurrent_primary_check(43, 14001, "DENY")
        concurrent_primary_check(42, 12001, "DENY")
        print(f"B62G9_{stage.upper()}_SIGNER_BLOCK_ENFORCED_ON_PRIMARY", flush=True)
    elif stage == "outage":
        # Primary is completely STOPPED, while independent stale copy allows.
        # No unapproved failover: authorization remains DENY.
        check_stale(43, 14001, "ALLOW")
        concurrent_primary_check(43, 14001, "ERROR")
        concurrent_primary_check(42, 11000, "ERROR")
        print("B62G9_PRIMARY_OUTAGE_NO_STALE_FALLBACK_DENY_PASS", flush=True)
    else:
        raise AssertionError("unsupported test mode")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", choices=("baseline", "postcommit", "blocked",
                                           "restarted", "outage"), required=True)
    run(parser.parse_args().stage)
