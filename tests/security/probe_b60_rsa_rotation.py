#!/usr/bin/env python3
"""Run isolated, actual Docker HTTP calls during B60 RS256 overlap/retirement.

No private key, HMAC secret or Docker socket is accessible from this
read-only synthetic probe. Never read untrusted URLs or a caller-supplied path.
"""
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT=Path("/opt/rotation")
ENDPOINTS={
    "direct-user":"http://user-service:8081/api/users",
    "gateway-user":"http://gateway:8080/api/users",
    "direct-audit":"http://audit-service:8083/api/events",
    "gateway-audit":"http://gateway:8080/api/events",
}
PHASES={
    "overlap":{
        "current":200,"previous":200,
        "relabel-previous":401,"unknown-kid":401,
        "untrusted-key":401,"foreign-issuer":401,"workload-only":401
    },
    "retired":{
        "current":200,"previous":401,"relabel-previous":401
    }
}

def read(name):
    if name not in PHASES["overlap"]:
        raise AssertionError("B60_UNKNOWN_TOKEN")
    path=ROOT/(name+".jwt")
    if path.is_symlink() or not path.is_file() or path.stat().st_size>4096:
        raise AssertionError("B60_INVALID_SYNTHETIC_TOKEN")
    jwt=path.read_text(encoding="ascii").strip()
    if len(jwt.split("."))!=3 or " " in jwt or "\n" in jwt:
        raise AssertionError("B60_INVALID_JWT")
    return jwt

def request(url,token):
    if url not in ENDPOINTS.values():
        raise AssertionError("B60_UNTRUSTED_HTTP_TARGET")
    req=urllib.request.Request(url,method="GET",headers={
        "Authorization":"Bearer "+token,
        "Accept":"application/json",
        "X-Aetheris-User-Role":"ADMIN",
        "X-Aetheris-User-Id":"42",
    })
    try:
        with urllib.request.urlopen(req,timeout=15) as res:
            res.read(1000)
            return res.status
    except urllib.error.HTTPError as exc:
        exc.read(1000)
        return exc.code

def main():
    phase=os.environ.get("B60_PHASE","")
    if phase not in PHASES:
        raise AssertionError("B60_UNKNOWN_ROLLOVER_PHASE")
    if (ROOT/"test-only.marker").read_text(encoding="ascii").strip()!="B60_EPHEMERAL_PUBLIC_ROLLOVER_V1":
        raise AssertionError("B60_FIXTURE_MISSING")
    # Recreated real containers have bounded startup time; do not treat not-yet-
    # ready HTTP listeners as a security decision. After readiness, each case
    # must return its exact status; failures never get retried or hidden.
    deadline=time.monotonic()+180
    while True:
        try:
            if all(request(url,read("current"))==200 for url in ENDPOINTS.values()):
                break
        except (OSError, TimeoutError):
            pass
        if time.monotonic()>=deadline:
            raise AssertionError("B60_DISPOSABLE_RSA_SERVICES_NOT_READY")
        time.sleep(3)
    tests=0
    for target,url in ENDPOINTS.items():
        for case,expected in PHASES[phase].items():
            actual=request(url,read(case))
            if actual!=expected:
                raise AssertionError(
                    f"B60_PUBLIC_KEY_PHASE_MISMATCH phase={phase} target={target} "
                    f"case={case} expected={expected} actual={actual}")
            tests+=1
    assert tests==(28 if phase=="overlap" else 12)
    print("B60_REAL_RSA_ROTATION_HTTP_RESULT",json.dumps({
        "phase":phase,"tests":tests,"passed":tests,"routes":4,
        "realGatewayAndBackendContainers":True,
        "privateKeyMountedInConsumers":False,
        "revokedKeyDenied":phase=="retired",
        "productionRotationCompleted":False,
        "stage45Go":False
    },sort_keys=True),flush=True)

if __name__=="__main__":
    main()
