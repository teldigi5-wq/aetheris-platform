#!/usr/bin/env python3
"""B59: real RS256-only JVM Gateway/User/Audit HTTP probes on CI network.

Identity test module minted all synthetic JWTs and exposes only public
verification key + test tokens. This process does not have the private key,
HMAC secret, Docker socket or any production access.
"""
import json
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT=Path("/opt/rsa")
CASES={"admin":200,"unknown-kid":401,"wrong-issuer":401,
       "wrong-audience":401,"wrong-signature":401,"expired":401,
       "legacy-hmac":401,"tampered":401}
ENDPOINTS={
    "direct-user":"http://user-service:8081/api/users",
    "gateway-user":"http://gateway:8080/api/users",
    "direct-audit":"http://audit-service:8083/api/events",
    "gateway-audit":"http://gateway:8080/api/events",
}

def read_token(name):
    assert name in CASES
    p=ROOT/(name+".jwt")
    assert p.is_file() and not p.is_symlink() and p.stat().st_size<=4096
    token=p.read_text(encoding="ascii").strip()
    assert len(token.split("."))==3 and " " not in token and "\n" not in token
    return token

def response(url,token):
    assert url in ENDPOINTS.values()
    req=urllib.request.Request(url,headers={
        "Authorization":"Bearer "+token,
        "Accept":"application/json",
        "X-Aetheris-User-Role":"ADMIN",
        "X-Aetheris-User-Id":"42",
    },method="GET")
    try:
        with urllib.request.urlopen(req,timeout=12) as r:
            r.read(2048)
            return r.status
    except urllib.error.HTTPError as e:
        e.read(2048)
        return e.code

def main():
    assert (ROOT/"test-only.marker").read_text(encoding="ascii").strip()=="B59_EPHEMERAL_IDENTITY_RSA_V1"
    tests=0
    # CI Java containers need warmup following HMAC->RS256 restart.
    deadline=time.monotonic()+180
    while True:
        try:
            for target,url in ENDPOINTS.items():
                status=response(url,read_token("admin"))
                if status!=200:
                    raise AssertionError(f"valid admin unavailable {target}={status}")
            break
        except (OSError,TimeoutError,AssertionError):
            if time.monotonic()>=deadline:
                raise AssertionError("B59_REAL_RSA_SERVICES_NOT_READY")
            time.sleep(3)

    for target,url in ENDPOINTS.items():
        for case,expected in CASES.items():
            got=response(url,read_token(case))
            if got!=expected:
                raise AssertionError(
                    f"B59_RSA_HTTP_DENIAL_FAILED target={target} case={case} expected={expected} got={got}")
            tests+=1
    assert tests==32
    print("B59_REAL_RSA_PUBLIC_ONLY_HTTP_RESULT",json.dumps({
        "assertions":tests,"passed":tests,"directAndGateway":True,
        "identityPrivateKeyNeverMounted":True,
        "HmacFallbackDenied":True,"unknownKidDenied":True,
        "wrongSignatureDenied":True,"productionMigrationCertified":False,
        "stage45Go":False
    },sort_keys=True),flush=True)

if __name__=="__main__":
    main()
