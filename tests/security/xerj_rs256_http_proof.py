#!/usr/bin/env python3
"""Extension of verified 25-check live proof with actual RS256 issuance and
HS256 downgrade denial across BOTH Gateway and direct resource servers.
The public token header is only inspected after real service verification.
"""
from __future__ import annotations
import base64
import hashlib
import hmac
import json
import os
import sys
import time
from pathlib import Path
import xerj_disposable_http_proof as baseline

def b64(data:bytes)->str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

def signed_hmac_attack():
    """Synthetic, obviously untrusted HMAC token that MUST fail under RSA-only mode."""
    now=int(time.time())
    header={"alg":"HS256","typ":"JWT","kid":os.environ["AETHERIS_JWT_RS256_KEY_ID"]}
    claims={"iss":os.environ.get("AETHERIS_JWT_ISSUER","aetheris-identity"),
            "aud":[os.environ.get("AETHERIS_JWT_AUDIENCE","aetheris-api")],
            "sub":"not-a-real-admin@example.test","uid":1234,
            "role":"ADMIN","scopes":["users:read","users:write","services:read"],
            "iat":now-20,"exp":now+180}
    signing_input=".".join((b64(json.dumps(header,sort_keys=True).encode()),
                             b64(json.dumps(claims,sort_keys=True).encode())))
    digest=hmac.new(b"synthetic-untrusted-hmac-secret-not-issued-by-identity-12345",
                    signing_input.encode(), hashlib.sha256).digest()
    return signing_input+"."+b64(digest)

def check_real_token_header(token):
    parts=token.split(".")
    baseline.verify(len(parts)==3,"identity_issues_compact_signed_jwt")
    raw=base64.urlsafe_b64decode(parts[0]+"="*(-len(parts[0])%4))
    obj=json.loads(raw)
    baseline.verify(obj.get("alg")=="RS256" and
        obj.get("kid")==os.environ["AETHERIS_JWT_RS256_KEY_ID"],
        "real_identity_emits_expected_rs256_kid")

def main():
    baseline.main()  # runs 25 real service and DB/AMQP assertions first, writes report
    import argparse
    parser=argparse.ArgumentParser()
    parser.add_argument("--report",type=Path,required=True)
    parser.add_argument("--compose",required=True)
    args=parser.parse_args()

    code, registration=baseline.request(8080,"POST","/api/auth/register",{
        "name":"RSA Header Verification",
        "email":"rsa-integrated-proof@aetheris.test",
        "password":"SyntheticPass123!Unique"})
    baseline.verify(code==201 and isinstance(registration,dict) and
        isinstance(registration.get("accessToken"),str),
        "fresh_rs256_consumer_registration_via_gateway")
    check_real_token_header(registration["accessToken"])

    fake=signed_hmac_attack()
    for route,port,path in (("gateway_user",8080,"/api/users"),
                             ("backend_user",8081,"/api/users"),
                             ("gateway_audit",8080,"/api/events"),
                             ("backend_audit",8083,"/api/events")):
        code,_=baseline.request(port,"GET",path,bearer=fake)
        baseline.verify(code==401,"rs256_rejects_hs256_downgrade_"+route)

    data=json.loads(args.report.read_text())
    data["evidence_class"]="HOSTED_DISPOSABLE_RS256_INTEGRATION"
    data["mTLS_verified"]=False
    data["checks"]=dict(baseline.CHECKS)
    data["status"]="PASS"
    args.report.write_text(json.dumps(data,sort_keys=True,indent=2)+"\n")
    print("XERJ_RS256_EXTENDED_LIVE_PROOF_PASS count="+str(len(data["checks"])))

if __name__=="__main__":
    try:
        main()
    except Exception as failure:
        # Avoid raw HTTP bodies or headers in logs even if a probe fails.
        print("XERJ_RS256_LIVE_HOLD reason="+type(failure).__name__,file=sys.stderr)
        raise SystemExit(1)
