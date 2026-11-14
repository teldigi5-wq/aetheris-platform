#!/usr/bin/env python3
"""B62-G10 actual PostgreSQL physical replication; primary-only synthetic reader.

Real password-authenticated psql from a separate isolated client container.
No real JWT processing and NEVER an application/production client.
"""
from __future__ import annotations

import argparse
import subprocess
from concurrent.futures import ThreadPoolExecutor

COMPOSE = ("docker", "compose",
           "-f", "tests/security/docker-compose.b62g-epoch-proof.yml",
           "-f", "tests/security/b62g10-physical-replica-overlay.yml")
READERS = {
    "b62g8_gateway_reader": "b62g8-gateway-synthetic-ci-only",
    "b62g8_user_reader": "b62g8-user-synthetic-ci-only",
    "b62g8_audit_reader": "b62g8-audit-synthetic-ci-only",
}
ISSUER = "b62g-identity"
TIMEOUT_SECONDS = 5


def raw_read(role: str, uid: int, iat: int, *, service: str = "postgres",
             kid: str = "key-current", incorrect_password: bool = False) -> str:
    if (role not in READERS or type(uid) is not int or uid not in (42, 43)
            or type(iat) is not int or iat not in (11000, 12001, 14001)
            or service not in ("postgres", "replica")
            or kid not in ("key-current", "key-unblocked")):
        return "ERROR"
    pw = "intentionally-invalid-b62g10" if incorrect_password else READERS[role]
    sql = ("SELECT b62g_proof.synthetic_post_signature_allowed("
           f"{uid},'{ISSUER}','{kid}',{iat});")
    argv = [*COMPOSE, "exec", "-T", "-e", f"PGPASSWORD={pw}",
            "-e", "PGCONNECT_TIMEOUT=1", "auth-probe",
            "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1", "-w",
            "-h", service, "-U", role, "-d", "b62g_synthetic", "-c", sql]
    try:
        response = subprocess.run(argv, text=True, capture_output=True,
                                  check=False, timeout=TIMEOUT_SECONDS)
    except (subprocess.TimeoutExpired, OSError):
        return "ERROR"
    if response.returncode != 0:
        return "ERROR"
    return {"t": "ALLOW", "f": "DENY"}.get(response.stdout.strip(), "ERROR")


def authorize_primary(role: str, uid: int, iat: int, kid: str = "key-current") -> str:
    """Fail closed. Never consult a standby, stale cache, or promoted replica."""
    return "ALLOW" if raw_read(role, uid, iat, kid=kid) == "ALLOW" else "DENY"


def expect(role: str, uid: int, iat: int, expected: str, *,
           service: str = "postgres", kid: str = "key-current") -> None:
    actual = raw_read(role, uid, iat, service=service, kid=kid)
    if actual != expected:
        raise AssertionError(
            f"B62G10_QUERY_MISMATCH role={role} side={service} "
            f"expected={expected} got={actual}"
        )
    if service == "postgres":
        decision = authorize_primary(role, uid, iat, kid)
        if decision != ("ALLOW" if expected == "ALLOW" else "DENY"):
            raise AssertionError("B62G10_UNSAFE_PRIMARY_AUTHORIZATION")


def concurrent_primary(uid: int, iat: int, expected: str,
                       kid: str = "key-current") -> None:
    work = [(r, uid, iat, expected, kid) for r in READERS for _ in range(3)]
    def verify(record):
        role, principal, epoch, answer, signed_kid = record
        expect(role, principal, epoch, answer, kid=signed_kid)
    with ThreadPoolExecutor(max_workers=3) as pool:
        list(pool.map(verify, work))
    print(f"B62G10_NINE_AUTHENTICATED_PRIMARY_READS_{expected}", flush=True)


def stage(name: str) -> None:
    if name == "baseline":
        for reader in READERS:
            expect(reader, 42, 11000, "ALLOW")
            expect(reader, 43, 14001, "ALLOW", service="replica")
        if raw_read("b62g8_gateway_reader", 42, 11000,
                    incorrect_password=True) != "ERROR":
            raise AssertionError("B62G10_INCORRECT_PASSWORD_ACCEPTED")
        if raw_read("b62g8_user_reader", 42, 11000,
                    service="replica", incorrect_password=True) != "ERROR":
            raise AssertionError("B62G10_REPLICA_INCORRECT_PASSWORD_ACCEPTED")
        print("B62G10_PRIMARY_AND_REAL_STANDBY_PASSWORD_AUTH_PASS", flush=True)
    elif name == "paused-epoch":
        for reader in READERS:
            expect(reader, 42, 11000, "DENY")
            expect(reader, 42, 11000, "ALLOW", service="replica")
        concurrent_primary(42, 11000, "DENY")
        print("B62G10_TRUE_WAL_REPLAY_LAG_STANDBY_ALLOWS_PRIMARY_DENIES",flush=True)
    elif name == "replayed-epoch":
        for reader in READERS:
            expect(reader, 42, 11000, "DENY", service="replica")
            expect(reader, 42, 12001, "ALLOW", service="replica")
            expect(reader, 43, 14001, "ALLOW")
        print("B62G10_REAL_STANDBY_REPLAY_CATCHUP_CONFIRMED",flush=True)
    elif name == "paused-kid":
        for reader in READERS:
            expect(reader, 43, 14001, "DENY")
            expect(reader, 43, 14001, "ALLOW", service="replica")
        print("B62G10_TRUE_WAL_REPLAY_LAG_COMPROMISED_KID",flush=True)
    elif name == "replayed-kid":
        concurrent_primary(43, 14001, "DENY")
        for reader in READERS:
            expect(reader, 43, 14001, "DENY", service="replica")
        print("B62G10_SIGNER_BLOCK_PHYSICAL_WAL_REPLAY_CONFIRMED",flush=True)
    elif name == "outage":
        # Standby remains readable; a different unblocked synthetic kid ALLOWs
        # on the standby. No primary means all authorization is still DENY.
        for reader in READERS:
            expect(reader, 43, 14001, "ALLOW",
                   service="replica", kid="key-unblocked")
            expect(reader, 43, 14001, "ERROR", kid="key-unblocked")
        for reader in READERS:
            if authorize_primary(reader, 43, 14001, "key-unblocked") != "DENY":
                raise AssertionError("B62G10_PROMOTED_UNTRUSTED_STANDBY")
        print("B62G10_PRIMARY_DOWN_STANDBY_ALLOWS_BUT_AUTH_DENIES", flush=True)
    else:
        raise AssertionError("Not a pinned CI stage")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", required=True, choices=(
        "baseline", "paused-epoch", "replayed-epoch", "paused-kid",
        "replayed-kid", "outage"))
    stage(parser.parse_args().stage)
