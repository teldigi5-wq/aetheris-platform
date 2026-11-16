#!/usr/bin/env python3
"""B62-G11: real Docker network cut/rejoin against physical WAL standby.

SYNTHETIC CI ONLY: no real JWT, customer data, HA election or production
authorization. This deliberately reuses the exact B62-G10 primary-only client.
"""
from __future__ import annotations

import argparse
import importlib.util
import time
from pathlib import Path

source = Path(__file__).with_name("b62g10-physical-replication-client.py")
spec = importlib.util.spec_from_file_location("b62g10_client", source)
g10 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g10)

# Bounded end-to-end process timeout includes startup, socket and Docker exec.
# This is TEST evidence, NOT a production p99/SLO guarantee.
MAX_SINGLE_AUTHORIZATION_SECONDS = 9.0


def checked_primary(role: str, uid: int, iat: int, expected: str) -> None:
    started = time.monotonic()
    actual = g10.authorize_primary(role, uid, iat)
    elapsed = time.monotonic() - started
    if elapsed > MAX_SINGLE_AUTHORIZATION_SECONDS:
        raise AssertionError("B62G11_TEST_AUTH_DECISION_EXCEEDED_BOUND")
    if actual != expected:
        raise AssertionError(
            f"B62G11_PRIMARY_AUTH_MISMATCH role={role} expected={expected} got={actual}"
        )


def main(stage: str) -> None:
    readers = tuple(g10.READERS)
    assert len(readers) == 3
    if stage == "replica-isolated":
        # The primary has committed a new epoch while the genuine physical
        # standby cannot receive WAL because its real network was severed.
        for role in readers:
            checked_primary(role, 42, 12001, "DENY")
            checked_primary(role, 43, 14001, "ALLOW")
            if g10.raw_read(role, 42, 12001, service="replica") != "ERROR":
                raise AssertionError("B62G11_DISCONNECTED_STANDBY_STILL_REACHABLE")
        print("B62G11_REAL_REPLICA_NETWORK_PARTITION_PRIMARY_AUTH_CONTINUES", flush=True)
    elif stage == "replica-rejoined-paused":
        # Reconnect does not imply fresh data. Replay is still deliberately
        # paused; the standby genuinely retains the old WAL state.
        for role in readers:
            if g10.raw_read(role, 42, 12001, service="replica") != "ALLOW":
                raise AssertionError("B62G11_MISSING_TRUE_STALE_ALLOW")
            checked_primary(role, 42, 12001, "DENY")
            checked_primary(role, 43, 14001, "ALLOW")
        print("B62G11_REJOINED_PHYSICAL_STANDBY_STILL_STALE_DENIED_BY_PRIMARY", flush=True)
    elif stage == "replica-caught-up":
        for role in readers:
            if g10.raw_read(role, 42, 12001, service="replica") != "DENY":
                raise AssertionError("B62G11_REPLICA_RECOVERY_DID_NOT_REPLAY_EPOCH")
            checked_primary(role, 42, 12001, "DENY")
            checked_primary(role, 43, 14001, "ALLOW")
        print("B62G11_REPLAY_RECOVERY_PRIMARY_AND_STANDBY_RECONCILED", flush=True)
    elif stage == "client-isolated":
        # Entire client container is severed, primary is still healthy.
        # No standby / cached allow failover is permitted for ANY role.
        for role in readers:
            if g10.raw_read(role, 43, 14001) != "ERROR":
                raise AssertionError("B62G11_DISCONNECTED_CLIENT_STILL_REACHED_PRIMARY")
            checked_primary(role, 43, 14001, "DENY")
            checked_primary(role, 42, 12001, "DENY")
        print("B62G11_CLIENT_NETWORK_PARTITION_FAIL_CLOSED_BOUNDED", flush=True)
    elif stage == "client-recovered":
        # Rejoining the private network must not reuse any previous ALLOW.
        for role in readers:
            checked_primary(role, 42, 12001, "DENY")
            checked_primary(role, 43, 14001, "ALLOW")
        print("B62G11_NETWORK_RECOVERY_WITHOUT_REVOKED_JWT_REPLAY", flush=True)
    else:
        raise AssertionError("unexpected B62-G11 CI phase")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", choices=(
        "replica-isolated", "replica-rejoined-paused", "replica-caught-up",
        "client-isolated", "client-recovered"), required=True)
    main(parser.parse_args().stage)
