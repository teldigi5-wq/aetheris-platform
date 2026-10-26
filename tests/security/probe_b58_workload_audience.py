#!/usr/bin/env python3
"""B58: real disposable backend/Gateway workload-audience confusion denial.

Read ONLY pre-minted synthetic Identity JWT files in an unprivileged probe
container. No signing secrets, Docker socket, production network or real data.
"""
import json
from pathlib import Path
import urllib.request
import urllib.error

ROOT = Path("/opt/tokens")
CASES = {"admin": 200, "workload": 401, "foreign": 401, "dual": 401}
TARGETS = (
    ("direct-user", "http://user-service:8081/api/users"),
    ("gateway-user", "http://gateway:8080/api/users"),
    ("direct-audit", "http://audit-service:8083/api/events"),
    ("gateway-audit", "http://gateway:8080/api/events"),
)


def read_token(name):
    assert name in CASES
    p = ROOT / (name + ".jwt")
    if p.is_symlink() or not p.is_file() or p.stat().st_size > 4096:
        raise AssertionError("B58_UNTRUSTED_SYNTHETIC_TOKEN_PATH")
    token = p.read_text(encoding="ascii").strip()
    if len(token.split(".")) != 3 or " " in token or "\n" in token:
        raise AssertionError("B58_MALFORMED_SYNTHETIC_TOKEN")
    return token


def request(url, bearer):
    if url not in dict(TARGETS).values():
        raise AssertionError("B58_UNTRUSTED_REQUEST_TARGET")
    headers = {"Accept": "application/json", "Authorization": "Bearer " + bearer,
               "X-Aetheris-User-Role": "ADMIN",
               "X-Aetheris-Scopes": "users:write services:read"}
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=12) as res:
            res.read(1000)
            return res.status
    except urllib.error.HTTPError as exc:
        exc.read(1000)
        return exc.code


def main():
    marker = (ROOT / "test-only.marker").read_text(encoding="ascii").strip()
    if marker != "IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1":
        raise AssertionError("B58_NOT_GENUINE_IDENTITY_JWT_FIXTURE")
    count = 0
    for target, url in TARGETS:
        for case, expected in CASES.items():
            status = request(url, read_token(case))
            if status != expected:
                raise AssertionError(
                    f"B58_AUDIENCE_BOUNDARY_FAILED target={target} case={case} "
                    f"expected={expected} actual={status}"
                )
            count += 1
    if count != 16:
        raise AssertionError("B58_MISSING_AUDIENCE_CASES")
    print("B58_REAL_API_WORKLOAD_BOUNDARY", json.dumps({
        "separateInternalProbeContainer": True,
        "targetCount": 4,
        "assertions": count,
        "passed": count,
        "adminAllowed": True,
        "workloadAudienceDenied": True,
        "ambiguousMultiAudienceDenied": True,
        "wrongIssuerDenied": True,
        "actualProductionWorkloadIdentityCertified": False,
        "stage45Go": False,
    }, sort_keys=True), flush=True)


if __name__ == "__main__":
    main()
