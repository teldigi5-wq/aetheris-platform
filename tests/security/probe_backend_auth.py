#!/usr/bin/env python3
"""B4P: real backend containers, direct attacker probes and actual Gateway routes.

CI-only with synthetic JwtService-issued identities and disposable services.
Never run against production, use real secrets, or mutate User/Audit records.
No shell execution, URLs or credentials from untrusted user input.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path("/opt/tokens")
TARGETS = {
    "user": ("http://user-service:8081", "/api/users"),
    "audit": ("http://audit-service:8083", "/api/events"),
}
GATEWAY = "http://gateway:8080"
TOKEN_NAMES = ("admin", "developer", "consumer", "expired", "tampered")
TOKEN_MARKER = "IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1"
RESULTS = []


def token(kind: str) -> str:
    if kind not in TOKEN_NAMES:
        raise AssertionError("UNREVIEWED_TOKEN_KIND")
    path = ROOT / (kind + ".jwt")
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 5000:
        raise AssertionError("TOKEN_PROVENANCE_INVALID")
    raw = path.read_text(encoding="ascii").strip()
    if len(raw.split(".")) != 3 or not raw.isascii() or " " in raw:
        raise AssertionError("NOT_A_SIGNED_JWT_FIXTURE")
    return raw


def request(base: str, route: str, kind: str | None,
            *, forged: bool = False, method: str = "GET") -> tuple[int, bytes]:
    assert base in (GATEWAY, *(x[0] for x in TARGETS.values()))
    assert route in ("/api/users", "/api/events")
    assert method in ("GET", "POST")
    headers = {"Accept": "application/json"}
    if kind is not None:
        headers["Authorization"] = "Bearer " + token(kind)
    if forged:
        headers["X-Aetheris-User-Id"] = "999999"
        headers["X-Aetheris-User-Role"] = "ADMIN"
        headers["X-Aetheris-Scopes"] = "users:write services:write"
    req = urllib.request.Request(
        base + route, headers=headers,
        data=b"" if method == "POST" else None, method=method
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as response:
            return response.status, response.read(40000)
    except urllib.error.HTTPError as ex:
        return ex.code, ex.read(40000)


def verify(step: str, result: int, expected: int, *, payload: bytes | None = None) -> None:
    RESULTS.append({"check": step, "status": result, "expected": expected,
                    "pass": result == expected})
    if result != expected:
        # Neither raw JWTs nor message bodies should leak to logs.
        raise AssertionError(f"B4P_DENIAL_OR_ROUTE_MISMATCH {step} expected={expected} got={result}")
    if payload is not None:
        assert payload[:1] in (b"[", b"{"), f"B4P_WRONG_RESPONSE_SHAPE_{step}"


def ready() -> None:
    # Wait for both actual backend applications AND production route IDs at
    # the actual Gateway to be live. The harness fails if Redis/DB/schema
    # errors are disguised by gateway fallback handlers.
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        try:
            for label, (base, route) in TARGETS.items():
                status, _ = request(base, route, "admin")
                if status != 200:
                    raise RuntimeError(f"{label} returned {status}")
                gateway_status, _ = request(GATEWAY, route, "admin")
                if gateway_status != 200:
                    raise RuntimeError(f"gateway/{label} returned {gateway_status}")
            print("B4P_REAL_SERVICE_AND_GATEWAY_READY", flush=True)
            return
        except (OSError, RuntimeError, TimeoutError):
            time.sleep(3)
    raise AssertionError("B4P_REAL_SERVICE_STARTUP_OR_ROUTING_FAILED")


def main() -> None:
    marker = (ROOT / "test-only.marker").read_text(encoding="ascii").strip()
    if marker != TOKEN_MARKER:
        raise AssertionError("B4P_NOT_GENUINE_IDENTITY_TEST_ISSUER")
    assert len({hashlib.sha256(token(t).encode()).hexdigest() for t in TOKEN_NAMES}) == 5
    ready()

    for label, (base, route) in TARGETS.items():
        # Direct access from a completely separate Docker container does
        # resolve service DNS; service-side auth must still reject attackers.
        for through, url in (("direct-container", base), ("actual-gateway", GATEWAY)):
            prefix = f"{through}:{label}"
            for kind, expected in (
                (None, 401),
                ("developer", 403),
                ("consumer", 403),
                ("expired", 401),
                ("tampered", 401),
            ):
                response, _ = request(url, route, kind, forged=True)
                verify(f"{prefix}:{kind or 'anonymous'}", response, expected)
            success, content = request(url, route, "admin", forged=True)
            verify(prefix + ":admin", success, 200, payload=content)

    # Gateway must reject an Audit mutation with only services:read BEFORE
    # exposing the backend; the backend itself has a matching independent
    # scope check even though it has no mapped write API.
    for channel, base in (("gateway", GATEWAY), ("direct-audit", TARGETS["audit"][0])):
        code, _ = request(base, "/api/events", "admin", forged=True, method="POST")
        verify(f"{channel}:read-only-admin-audit-post", code, 403)

    assert len(RESULTS) == 26, len(RESULTS)
    print("B4P_DISPOSABLE_REAL_SERVICE_AUTH_RESULT",
          json.dumps({
            "directContainerAndGateway": True, "testAssertions": len(RESULTS),
            "passed": sum(r["pass"] for r in RESULTS),
            "syntheticIdentityJwt": True,
            "noProductionCredentials": True,
            "realBackendContainers": True,
            "objectOwnershipCertified": False,
            "productionNetworkPolicyCertified": False,
            "stage45Go": False,
        }, sort_keys=True), flush=True)


if __name__ == "__main__":
    main()
