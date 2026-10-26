#!/usr/bin/env python3
"""B62-E disposable real HTTP verifier policy and synthetic Identity startup probe.

No private RSA key, signing secret, Docker socket, attacker-supplied URL or
production credentials may enter the probe container. No retries on wrong
security status codes after the readiness gate has succeeded.
"""
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT = Path("/opt/rotation")
ROUTES = {
    "direct-user": "http://user-service:8081/api/users",
    "gateway-user": "http://gateway:8080/api/users",
    "direct-audit": "http://audit-service:8083/api/events",
    "gateway-audit": "http://gateway:8080/api/events",
}
CASES = {
    "current": 200,
    "previous": 200,
    "max-lifetime": 200,
    "overlong-lifetime": 401,
    "zero-lifetime": 401,
}
IDENTITY_HEALTH = "http://identity-lifetime:8082/actuator/health"


def read_token(name):
    if name not in CASES:
        raise AssertionError("B62E_UNAPPROVED_TOKEN_NAME")
    p = ROOT / (name + ".jwt")
    if p.is_symlink() or not p.is_file() or p.stat().st_size > 4096:
        raise AssertionError("B62E_MISSING_OR_UNSAFE_SYNTHETIC_TOKEN")
    token = p.read_text(encoding="ascii").strip()
    if len(token.split(".")) != 3 or not token.isascii() or " " in token:
        raise AssertionError("B62E_INVALID_RSA_FIXTURE")
    return token


def request(url, token=None):
    if url not in ROUTES.values() and url != IDENTITY_HEALTH:
        raise AssertionError("B62E_UNTRUSTED_ENDPOINT")
    headers = {"Accept": "application/json"}
    if token is not None:
        headers["Authorization"] = "Bearer " + token
        # Deliberate attacker-controlled identity headers must not authorize.
        headers["X-Aetheris-User-Role"] = "ADMIN"
        headers["X-Aetheris-User-Id"] = "42"
    req = urllib.request.Request(url, method="GET", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=12) as res:
            res.read(1000)
            return res.status
    except urllib.error.HTTPError as error:
        error.read(1000)
        return error.code


def wait_ready(urls, credentials):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            statuses = [request(url, credentials) for url in urls]
            if all(status == 200 for status in statuses):
                return
        except (OSError, TimeoutError):
            pass
        time.sleep(3)
    raise AssertionError("B62E_REAL_CONTAINER_STARTUP_OR_ROUTING_FAILED")


def main():
    mode = os.environ.get("B62E_MODE", "jwt")
    if mode == "identity-ready":
        wait_ready((IDENTITY_HEALTH,), None)
        print("B62E_SYNTHETIC_IDENTITY_STARTED_WITH_VALID_3600S_TTL", flush=True)
        return
    if mode != "jwt":
        raise AssertionError("B62E_UNREVIEWED_MODE")
    marker = ROOT / "test-only.marker"
    if marker.is_symlink() or marker.read_text(encoding="ascii").strip() != "B60_EPHEMERAL_PUBLIC_ROLLOVER_V1":
        raise AssertionError("B62E_UNVERIFIED_IDENTITY_SIGNING_FIXTURES")
    wait_ready(ROUTES.values(), read_token("current"))
    checks = 0
    for name, url in ROUTES.items():
        for case, expected in CASES.items():
            received = request(url, read_token(case))
            if received != expected:
                raise AssertionError(
                    f"B62E_RSA_HTTP_FAILURE route={name} case={case} expected={expected} actual={received}"
                )
            checks += 1
    assert checks == 20
    print("B62E_REAL_DISPOSABLE_RSA_HTTP", json.dumps({
        "routes": 4, "checks": checks, "passed": checks,
        "maxLifetimeSec": 3600, "oldKeyGraceOnly": True,
        "productionCertified": False, "stage45Go": False,
    }, sort_keys=True), flush=True)


if __name__ == "__main__":
    main()
