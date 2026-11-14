#!/usr/bin/env python3
"""B4R: test-only signed User API seed/restore contract.

No external URL or custom HTTP destinations. Database operations outside this
script are restricted to disposable PostgreSQL inside CI.
"""
from __future__ import annotations
import json
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request

TOKEN_ROOT=Path("/opt/tokens")
ADMIN="admin"
FIXTURE={"name":"B4R disposable restored user",
         "email":"b4r-rehearsal-only@example.invalid"}
HOSTS={"original":"http://user-service:8081",
       "restored":"http://user-validated:8081"}
CHECKS=0


def jwt(name:str)->str:
    if name not in ("admin","consumer","developer","expired","tampered"):
        raise AssertionError("B4R_UNAPPROVED_TOKEN")
    assert (TOKEN_ROOT/"test-only.marker").read_text().strip()=="IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1"
    path=TOKEN_ROOT/(name+".jwt")
    if path.is_symlink() or path.stat().st_size>6000:
        raise AssertionError("B4R_BAD_TOKEN_FILE")
    value=path.read_text(encoding="ascii").strip()
    assert len(value.split("."))==3
    return value


def query(which:str,route:str,kind:str,
          method:str="GET",body:dict|None=None)->tuple[int,bytes]:
    assert which in HOSTS
    assert route=="/api/users" or (route.startswith("/api/users/") and route[11:].isdigit() and 0<int(route[11:])<=1000)
    assert method in ("GET","POST")
    data=json.dumps(body).encode() if body is not None else None
    headers={"Authorization":"Bearer "+jwt(kind),"Accept":"application/json"}
    if data is not None:
        assert method=="POST" and route=="/api/users"
        headers["Content-Type"]="application/json"
    request=urllib.request.Request(HOSTS[which]+route,method=method,
        headers=headers,data=data)
    try:
        with urllib.request.urlopen(request,timeout=9) as reply:
            return reply.status,reply.read(6000)
    except urllib.error.HTTPError as e:
        return e.code,e.read(6000)


def check(label:str,got:int,want:int)->None:
    global CHECKS
    CHECKS+=1
    if got!=want:
        raise AssertionError(f"B4R_CHECK_{label}_EXPECTED_{want}_GOT_{got}")


def ready(which:str)->None:
    deadline=time.monotonic()+125
    while time.monotonic()<deadline:
        try:
            code,_=query(which,"/api/users","admin")
            if code==200:return
        except OSError:pass
        time.sleep(3)
    raise AssertionError("B4R_VALIDATION_STARTUP_UNAVAILABLE_"+which)


def main()->None:
    if len(sys.argv)!=2 or sys.argv[1] not in ("seed","verify"):
        raise AssertionError("B4R_EXACT_SEED_OR_VERIFY_ONLY")
    which=sys.argv[1]
    ready("original" if which=="seed" else "restored")
    if which=="seed":
        status,data=query("original","/api/users","admin")
        check("original-empty",status,200)
        assert json.loads(data)==[], "B4R_MUST_SEED_FRESH_EMPTY_TEST_DATABASE"
        status,data=query("original","/api/users","admin",method="POST",body=FIXTURE)
        check("admin-synthetic-seed",status,201)
        row=json.loads(data)
        assert isinstance(row.get("id"),int) and 0<row["id"]<=1000 and all(row.get(k)==v for k,v in FIXTURE.items()), "B4R_SYNTHETIC_USER_ROW_NOT_EXACT"
        print("B4R_PRE_BACKUP_REAL_HTTP_SYNTHETIC_ROW_SEEDED",
              json.dumps({"checks":CHECKS,"onlySyntheticData":True,"rowId":row["id"],
                          "stage45Go":False},sort_keys=True),flush=True)
        return

    status,data=query("restored","/api/users","admin")
    check("restored-list",status,200)
    rows=json.loads(data)
    assert isinstance(rows,list) and len(rows)==1 and isinstance(rows[0],dict)
    row=rows[0]
    assert isinstance(row.get("id"),int) and 0<row["id"]<=1000
    assert all(row.get(k)==v for k,v in FIXTURE.items()),"B4R_RESTORED_ROW_CHANGED"
    route="/api/users/"+str(row["id"])
    for kind,want in (("admin",200),("consumer",403),("developer",403),
                      ("expired",401),("tampered",401)):
        status,data=query("restored",route,kind)
        check("restored-"+kind,status,want)
        if kind=="admin":
            assert json.loads(data)==row,"B4R_RESTORED_ITEM_CHANGED"
    status,data=query("original",route,"admin")
    check("original-unchanged",status,200)
    assert json.loads(data)==row
    assert CHECKS==7
    print("B4R_REAL_RESTORED_USER_HIBERNATE_VALIDATE_RESULT",
          json.dumps({"httpChecks":7,"passed":7,"restoredSyntheticRow":1,
                      "realUserServiceImage":True,"ddlAuto":"validate",
                      "productionSchemaCertified":False,
                      "historicalSchemaCertified":False,"stage45Go":False},
                     sort_keys=True),flush=True)


if __name__=="__main__":
    main()
