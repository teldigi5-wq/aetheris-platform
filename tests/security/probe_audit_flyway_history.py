#!/usr/bin/env python3
"""B4T: separate real Audit images on explicitly versioned empty SQL schema.

Test-only containers, no production credentials, networking or writes.
No invented Audit write route or fake API success.
"""
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

ROOT=Path("/opt/tokens")
SERVICES={"first":"http://audit-fresh:8083/api/events",
          "second":"http://audit-second:8083/api/events"}
TESTS=0


def jwt(name):
    assert name in ("admin","developer","consumer","expired","tampered")
    assert (ROOT/"test-only.marker").read_text(encoding="ascii").strip()=="IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1"
    path=ROOT/(name+".jwt")
    assert path.is_file() and not path.is_symlink() and path.stat().st_size<6000
    token=path.read_text(encoding="ascii").strip()
    assert len(token.split("."))==3
    return token


def request(service,role):
    assert service in SERVICES
    headers={"Accept":"application/json",
             "X-Aetheris-User-Role":"ADMIN",
             "X-Aetheris-Scopes":"services:read"}
    if role is not None:
        headers["Authorization"]="Bearer "+jwt(role)
    req=urllib.request.Request(SERVICES[service],headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=7) as res:
            return res.status,res.read(6000)
    except urllib.error.HTTPError as e:
        return e.code,e.read(6000)


def ready(service):
    deadline=time.monotonic()+125
    while time.monotonic()<deadline:
        try:
            code,_=request(service,"admin")
            if code==200:return
        except OSError:pass
        time.sleep(3)
    raise AssertionError("B4T_ACTUAL_AUDIT_SERVICE_NOT_READY_"+service)


def main():
    global TESTS
    for service in ("first","second"):
        ready(service)
        for role,expected in ((None,401),("developer",403),("consumer",403),
                              ("expired",401),("tampered",401),("admin",200)):
            code,payload=request(service,role)
            TESTS+=1
            if code!=expected:
                raise AssertionError("B4T_"+service+"_"+str(role)+"_EXPECTED_"+str(expected)+"_GOT_"+str(code))
            if role=="admin":
                assert json.loads(payload)==[], "B4T_UNEXPECTED_AUDIT_RECORDS"
    assert TESTS==12
    print("B4T_AUDIT_FLYWAY_EXPLICIT_HISTORY_HTTP_RESULT",
          json.dumps({"realAuditImages":2,"httpChecks":TESTS,"passed":TESTS,
                      "syntheticIdentityTokens":True,"autoBaselineAllowed":False,
                      "historicOperatorSchemaReviewed":False,
                      "releaseApproved":False},sort_keys=True),flush=True)


if __name__=="__main__":
    main()
