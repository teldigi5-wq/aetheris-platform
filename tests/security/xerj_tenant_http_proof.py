#!/usr/bin/env python3
"""Real tenant-bounded API verification on top of all 47 mTLS/RS256 checks.

This script tests a newly created, server-minted *account-bounded tenant*.
It does not claim enterprise tenant membership, tenant-scoped Audit tables or
production ownership migration. No JWT/PII or credentials are printed.
"""
from __future__ import annotations
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import uuid
import xerj_real_mtls_http_proof as mtls

baseline = mtls.baseline

def signed_tenant_claim(jwt):
    segments=jwt.split(".")
    baseline.verify(len(segments)==3,"tenant_identity_compact_jwt")
    decoded=json.loads(base64.urlsafe_b64decode(segments[1]+"="*(-len(segments[1])%4)))
    tid=decoded.get("tid")
    try:
        tenant=uuid.UUID(tid)
    except (ValueError, TypeError, AttributeError):
        raise AssertionError("missing-or-invalid-issuer-tenant")
    baseline.verify(str(tenant)==tid,"identity_emits_canonical_server_tenant_uuid")
    baseline.verify(decoded.get("role")=="API_CONSUMER","identity_scoped_account_role")
    return tenant

def register(suffix):
    code,response=mtls.ORIGINAL_REQUEST(8080,"POST","/api/auth/register",{
        "name":"Synthetic "+suffix,
        "email":f"tenant-{suffix.lower()}@aetheris.test",
        "password":"SyntheticPass123!Unique"})
    baseline.verify(code==201 and isinstance(response,dict) and
        isinstance(response.get("accessToken"),str),
        "tenant_registration_via_actual_gateway_"+suffix.lower())
    return response["accessToken"]

def gateway(method,path,token=None,payload=None,headers=None):
    return mtls.ORIGINAL_REQUEST(8080,method,path,payload=payload,
                                bearer=token,extra=headers)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--report",type=Path,required=True)
    parser.add_argument("--compose",required=True)
    args=parser.parse_args()

    mtls.main()  # Full 47 verified handshake, auth, DB and AMQP checks first.
    a=register("Alpha")
    b=register("Beta")
    tenant_a=signed_tenant_claim(a)
    tenant_b=signed_tenant_claim(b)
    baseline.verify(tenant_a!=tenant_b,"identity_separates_registered_account_tenants")

    code,items=gateway("GET","/api/users/tenant",a)
    baseline.verify(code==200 and isinstance(items,list) and len(items)==0,
                    "tenant_a_reads_only_own_initial_empty_records")
    code,items=gateway("GET","/api/users/tenant",b)
    baseline.verify(code==200 and isinstance(items,list) and len(items)==0,
                    "tenant_b_reads_only_own_initial_empty_records")

    code,created_a=gateway("POST","/api/users/tenant",a,{
        "name":"Alpha synthetic record","email":"alpha-owned@aetheris.test"})
    baseline.verify(code==201 and isinstance(created_a,dict) and
                    isinstance(created_a.get("id"),int),
                    "tenant_a_creates_server_owned_postgres_record")
    id_a=created_a["id"]

    code,created_b=gateway("POST","/api/users/tenant",b,{
        "name":"Beta synthetic record","email":"beta-owned@aetheris.test"},
        {"X-Aetheris-Tenant-Id":str(tenant_a)})
    baseline.verify(code==201 and isinstance(created_b,dict) and
                    isinstance(created_b.get("id"),int) and created_b["id"]!=id_a,
                    "tenant_b_forged_header_cannot_control_record_owner")
    id_b=created_b["id"]

    code,items=gateway("GET","/api/users/tenant",a)
    baseline.verify(code==200 and isinstance(items,list) and
        {item["id"] for item in items}=={id_a},"tenant_a_list_is_sql_scoped")

    code,items=gateway("GET","/api/users/tenant",b,headers={
        "X-Aetheris-Tenant-Id":str(tenant_a),
        "X-Aetheris-User-Id":"1",
        "X-Aetheris-User-Role":"ADMIN"})
    baseline.verify(code==200 and isinstance(items,list) and
        {item["id"] for item in items}=={id_b},
        "tenant_b_forged_headers_cannot_read_tenant_a")

    for label,token,foreign_id in (("a",a,id_b),("b",b,id_a)):
        code,_=gateway("GET",f"/api/users/tenant/{foreign_id}",token)
        baseline.verify(code==404,"tenant_"+label+"_cannot_read_foreign_known_id")
        code,_=gateway("DELETE",f"/api/users/tenant/{foreign_id}",token)
        baseline.verify(code==404,"tenant_"+label+"_cannot_delete_foreign_known_id")
        code,_=mtls.direct_https(8081,"GET",f"/api/users/tenant/{foreign_id}",
                                bearer=token)
        baseline.verify(code==404,
            "tenant_"+label+"_cannot_bypass_owner_predicate_via_direct_mtls_backend")
        code,_=gateway("GET","/api/users",token)
        baseline.verify(code==403,"tenant_"+label+"_cannot_list_admin_global_user_data")
        code,_=gateway("GET","/api/events",token)
        baseline.verify(code==403,"tenant_"+label+"_cannot_list_global_audit_pii")

    code,data=gateway("GET",f"/api/users/tenant/{id_a}",a)
    baseline.verify(code==200 and data.get("id")==id_a,
                    "tenant_a_reads_own_owned_record")
    code,data=gateway("GET",f"/api/users/tenant/{id_b}",b)
    baseline.verify(code==200 and data.get("id")==id_b,
                    "tenant_b_reads_own_owned_record")

    code,_=gateway("GET","/api/users/tenant",headers={
        "X-Aetheris-Tenant-Id":str(tenant_a)})
    baseline.verify(code==401,"unsigned_tenant_header_without_token_rejected")

    for label,token,id in (("a",a,id_a),("b",b,id_b)):
        code,_=gateway("DELETE",f"/api/users/tenant/{id}",token)
        baseline.verify(code==204,"tenant_"+label+"_can_delete_own_record")

    report=json.loads(args.report.read_text(encoding="utf-8"))
    report["checks"]=dict(baseline.CHECKS)
    report["account_bounded_tenant_user_api_verified"]=True
    report["tenant_isolation_verified"]=False
    report["tenant_membership_verified"]=False
    report["production_verified"]=False
    report["production_mTLS_verified"]=False
    report["migration_applied_to_production"]=False
    report["status"]="PASS"
    args.report.write_text(json.dumps(report,sort_keys=True,indent=2)+"\n",
                            encoding="utf-8")
    print("XERJ_TENANT_BOUNDED_LIVE_PASS checks="+str(len(report["checks"])))

if __name__=="__main__":
    try:
        main()
    except Exception as error:
        print("XERJ_TENANT_BOUNDARY_HOLD reason="+type(error).__name__,file=sys.stderr)
        raise SystemExit(1)
