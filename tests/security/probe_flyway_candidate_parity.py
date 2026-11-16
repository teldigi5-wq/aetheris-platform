#!/usr/bin/env python3
"""B4S: actual modern Identity/User Spring HTTP against PR247 synthetic lab SQL.

This read-only probe must use the real Identity issuer JWT fixture from CI.
It does not connect to a public endpoint, grant access or mutate any rows.
"""
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

TOKENS=Path("/opt/tokens")
USERS="http://user-flyway-validated:8081/api/users"
IDENTITY="http://identity-validated:8082/actuator/health"
CHECKS=0


def request(url, jwt=None):
    assert url in (USERS, IDENTITY), "B4S_UNEXPECTED_HOST"
    headers={"Accept":"application/json"}
    if jwt is not None:
        assert jwt in ("admin","developer","consumer","expired","tampered")
        assert (TOKENS/"test-only.marker").read_text().strip()=="IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1"
        p=TOKENS/(jwt+".jwt")
        assert p.is_file() and not p.is_symlink() and p.stat().st_size<6000
        token=p.read_text(encoding="ascii").strip()
        assert len(token.split("."))==3
        headers["Authorization"]="Bearer "+token
    try:
        with urllib.request.urlopen(urllib.request.Request(url,headers=headers),timeout=7) as response:
            return response.status,response.read(4000)
    except urllib.error.HTTPError as err:
        return err.code,err.read(4000)


def expect(label, code, want):
    global CHECKS
    CHECKS+=1
    if code!=want:
        raise AssertionError(f"B4S_HTTP_{label}_EXPECTED_{want}_GOT_{code}")


def wait(url, role):
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        try:
            code,_=request(url,role)
            if code==200:return
        except OSError:pass
        time.sleep(3)
    raise AssertionError("B4S_VALIDATE_APP_NOT_READY")


def main():
    wait(IDENTITY,None)
    wait(USERS,"admin")
    code,body=request(IDENTITY)
    expect("identity-health",code,200)
    status=json.loads(body)
    assert status.get("status")=="UP", "B4S_IDENTITY_HEALTH_NOT_UP"
    for role,want in ((None,401),("developer",403),("consumer",403),
                      ("expired",401),("tampered",401),("admin",200)):
        code,body=request(USERS,role)
        expect("user-"+str(role),code,want)
        if role=="admin":
            assert json.loads(body)==[], "B4S_CANDIDATE_SCHEMA_MUST_BE_EMPTY"
    assert CHECKS==7
    print("B4S_REAL_MODERN_IDENTITY_USER_VALIDATE_CANDIDATE_RESULT",
          json.dumps({"httpChecks":CHECKS,"passed":CHECKS,"postgresOnlySynthetic":True,
                      "sqlSnapshotSource":"PR247@61d635f",
                      "realModernIdentityUserImages":True,"ddlAuto":"validate",
                      "flywayRunBySpring":False,"historicalOperatorDataChecked":False,
                      "productionMigrationCertified":False,"stage45Go":False},
                     sort_keys=True),flush=True)


if __name__=="__main__":
    main()
