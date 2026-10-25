#!/usr/bin/env python3
"""B4Q: deny User-row access for signed Identity uid equal to unrelated row id.

Runs only on disposable Compose test network; fresh synthetic row is created and
deleted by a valid ADMIN token, never against real data or production services.
"""
from __future__ import annotations
import json
from pathlib import Path
import urllib.error
import urllib.request

ROOT=Path("/opt/tokens")
USER="http://user-service:8081"
GATEWAY="http://gateway:8080"
BASES=(("direct",USER),("gateway",GATEWAY))
CASES=("consumer-uid1","developer-uid1")
EXPECTED_MARKER="IDENTITY_ISSUER_DIRECT_JWT_CONTRACT_V1"
COUNTS=[]
FIXTURE={"name":"B4Q synthetic non-owner row",
         "email":"b4q-synthetic-row@example.invalid"}


def jwt(role:str)->str:
    if role not in ("admin",*CASES):
        raise AssertionError("B4Q_UNREVIEWED_JWT_NAME")
    p=ROOT/(role+".jwt")
    if p.is_symlink() or not p.is_file() or p.stat().st_size>6000:
        raise AssertionError("B4Q_INVALID_SYNTHETIC_JWT_FILE")
    value=p.read_text(encoding="ascii").strip()
    if len(value.split("."))!=3 or " " in value:
        raise AssertionError("B4Q_INVALID_JWT_WIRE_FORMAT")
    return value


def http(base:str,method:str,path:str,role:str,
         *,forge:bool=False,body:bytes|None=None)->tuple[int,bytes]:
    assert base in (USER,GATEWAY), "B4Q_EXTERNAL_HTTP_ADDRESS_FORBIDDEN"
    assert method in ("GET","POST","DELETE")
    assert path in ("/api/users","/api/users/1")
    headers={"Accept":"application/json","Authorization":"Bearer "+jwt(role)}
    if forge:
        headers.update({"X-Aetheris-User-Id":"1",
                        "X-Aetheris-User-Role":"ADMIN",
                        "X-Aetheris-Scopes":"users:write users:read",
                        "X-Forwarded-User-Id":"1"})
    if body is not None:
        assert method=="POST"
        headers["Content-Type"]="application/json"
    req=urllib.request.Request(base+path,data=body,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=12) as rsp:
            return rsp.status,rsp.read(8000)
    except urllib.error.HTTPError as err:
        return err.code,err.read(8000)


def verify(label:str,got:int,want:int)->None:
    COUNTS.append((label,got==want))
    if got!=want:
        raise AssertionError(f"B4Q_HTTP_SCOPE_FAIL {label}: expected={want} got={got}")


def parsed(payload:bytes)->object:
    assert 0<len(payload)<=8000
    return json.loads(payload.decode("utf-8"))


def main()->None:
    assert (ROOT/"test-only.marker").read_text(encoding="ascii").strip()==EXPECTED_MARKER
    assert jwt("consumer-uid1")!=jwt("developer-uid1")!=jwt("admin")
    # Fail closed if this disposable test DB has already been used/sealed.
    for label,base in BASES:
        code,payload=http(base,"GET","/api/users","admin")
        verify(label+":empty-admin-list",code,200)
        assert parsed(payload)==[], "B4Q_EXPECTED_FRESH_EMPTY_SYNTHETIC_DB"

    payload=json.dumps(FIXTURE,sort_keys=True).encode("utf-8")
    code,body=http(USER,"POST","/api/users","admin",body=payload)
    verify("admin-create-single-synthetic-user",code,201)
    row=parsed(body)
    assert isinstance(row,dict) and row.get("id")==1, "B4Q_ID_COLLISION_NOT_PROVEN"
    assert row.get("email")==FIXTURE["email"] and row.get("name")==FIXTURE["name"]
    expected=row

    for label,base in BASES:
        code,resp=http(base,"GET","/api/users/1","admin")
        verify(label+":admin-read-synthetic-row",code,200)
        assert parsed(resp)==expected

    # Identity uid=1 is NOT ownership of a User id=1. The two entity tables
    # are independent; deny nonadmins with exactly matching numeric IDs even
    # though DEVELOPER holds users:write in its authentic signed scope.
    for who in CASES:
        for label,base in BASES:
            for method,path,body_,forged in (
                ("GET","/api/users/1",None,False),
                ("GET","/api/users",None,False),
                ("GET","/api/users/1",None,True),
                ("DELETE","/api/users/1",None,True),
                ("POST","/api/users",payload,True),
            ):
                status,_=http(base,method,path,who,body=body_,forge=forged)
                verify(f"{who}:{label}:{method}:{path}:forged={forged}",status,403)

    # Prove every denied action was nonmutating before sanctioned cleanup.
    for label,base in BASES:
        code,resp=http(base,"GET","/api/users/1","admin")
        verify(label+":row-unchanged-after-nonadmin-probes",code,200)
        assert parsed(resp)==expected

    status,_=http(GATEWAY,"DELETE","/api/users/1","admin")
    verify("admin-delete-synthetic-row-only",status,204)
    for label,base in BASES:
        code,_=http(base,"GET","/api/users/1","admin")
        verify(label+":row-absent-after-approved-admin-deletion",code,404)
    assert len(COUNTS)==30, f"B4Q_EXPECTED_30_HARDENED_HTTP_CHECKS_NOT_{len(COUNTS)}"
    assert all(p for _,p in COUNTS)
    print("B4Q_SIGNED_UID_NOT_USER_ROW_OWNER_RESULT",
          json.dumps({"passed":len(COUNTS),"actualSeparateContainers":True,
                      "genuineIdentityIssuerUsed":True,"uidEqualsRowId":1,
                      "nonAdminRowOwnershipGranted":False,
                      "adminOnlyConstraintVerified":True,
                      "syntheticDataOnly":True,"productionOrTenantCertified":False,
                      "stage45Go":False},sort_keys=True),flush=True)


if __name__=="__main__":
    main()
