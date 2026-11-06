#!/usr/bin/env python3
"""Real TLS sockets: Spring Gateway -> Tomcat user/audit with mutual cert auth.

Reuses verified RSA-only 32-check proof, substituting CA-verified TLS for
direct backend requests. No remote service or production credentials involved.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, ProxyHandler, HTTPSHandler, build_opener
import xerj_rs256_http_proof as rsa

baseline = rsa.baseline
INSECURE_HTTP_PORTS = (8080, 8082)
HTTPS_SERVICES = {8081: "user-service", 8083: "audit-service"}
DIR = Path(os.environ["RUNNER_TEMP"]) / "xerj-mtls-fixture"
ORIGINAL_REQUEST = baseline.request

def context(ca: str, client: str | None, hostname_check=True) -> ssl.SSLContext:
    sslctx = ssl.create_default_context(cafile=str(DIR / ca))
    sslctx.check_hostname = hostname_check
    if client:
        sslctx.load_cert_chain(
            certfile=str(DIR / (client + ".crt")),
            keyfile=str(DIR / (client + ".key")))
    return sslctx

def direct_https(port, method, path, payload=None, bearer=None, extra=None,
                 ca="ca.crt", client="gateway-client", hostname=None):
    host = hostname if hostname is not None else HTTPS_SERVICES[port]
    assert port in HTTPS_SERVICES
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    if extra:
        headers.update(extra)
    req = Request(f"https://{host}:{port}{path}",
                  data=data, headers=headers, method=method)
    opener = build_opener(ProxyHandler({}), HTTPSHandler(context=context(ca, client)))
    try:
        with opener.open(req, timeout=7) as response:
            code = response.status
            raw = response.read(200001)
    except HTTPError as error:
        code = error.code
        raw = error.read(200001)
    if len(raw) > 200000:
        raise AssertionError("response-size-boundary-failed")
    try:
        document = json.loads(raw) if raw else None
    except (UnicodeDecodeError, json.JSONDecodeError):
        document = None
    return code, document

def routed_request(port, method, path, payload=None, bearer=None, extra=None):
    if port in HTTPS_SERVICES:
        return direct_https(port, method, path, payload, bearer, extra)
    return ORIGINAL_REQUEST(port, method, path, payload, bearer, extra)

def must_fail_tls(port, method="/api/users", bearer=None, ca="ca.crt",
                  client=None, hostname=None, extra=None):
    try:
        code, _ = direct_https(port, "GET", method, bearer=bearer,
                               ca=ca, client=client, hostname=hostname, extra=extra)
        raise AssertionError("Expected TLS rejection, received HTTP " + str(code))
    except (URLError, ssl.SSLError, ConnectionResetError, BrokenPipeError, TimeoutError):
        return True

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--compose", required=True)
    args = parser.parse_args()
    baseline.request = routed_request
    rsa.main()  # 32 previously passing checks, real HTTPS direct backends
    code, login = ORIGINAL_REQUEST(8080, "POST", "/api/auth/login", {
        "email": "e2e-admin@aetheris.test",
        "password": "SyntheticPass123!Unique"})
    baseline.verify(code == 200 and isinstance(login,dict) and
        isinstance(login.get("accessToken"),str), "mtls_real_identity_admin_login")
    admin = login["accessToken"]
    for tag,port,path in (("user",8081,"/api/users"),
                          ("audit",8083,"/api/events")):
        baseline.verify(must_fail_tls(port,method=path,bearer=admin),
            tag+"_client_certificate_required_at_actual_tls_handshake")
        baseline.verify(must_fail_tls(port,method=path,bearer=admin,
            extra={"X-Forwarded-Client-Cert":
                   "spiffe://aetheris.test/ns/security/sa/gateway"}),
            tag+"_spoofed_forwarded_cert_cannot_replace_tls_handshake")
        code,_ = direct_https(port,"GET",path,bearer=admin,client="wrong-client")
        baseline.verify(code == 403,tag+"_trusted_ca_wrong_spiffe_denied_by_servlet")
        baseline.verify(must_fail_tls(port,method=path,bearer=admin,
            ca="untrusted-ca.crt",client="gateway-client"),
            tag+"_untrusted_server_ca_rejected")
        baseline.verify(must_fail_tls(port,method=path,bearer=admin,
            client="gateway-client", hostname="127.0.0.1"),
            tag+"_server_hostname_mismatch_rejected")
        code,_ = direct_https(port,"GET",path,bearer="invalid.token",
                              client="gateway-client")
        baseline.verify(code == 401,
            tag+"_trusted_gateway_certificate_does_not_override_invalid_jwt")
        code,_ = direct_https(port,"GET",path,bearer=admin,
                              client="gateway-client")
        baseline.verify(code == 200,
            tag+"_valid_tls_gateway_certificate_and_admin_jwt_accepted")

    report = json.loads(args.report.read_text(encoding="utf-8"))
    report["checks"] = dict(baseline.CHECKS)
    report["evidence_class"] = "HOSTED_DISPOSABLE_REAL_GATEWAY_TO_TOMCAT_MTLS"
    report["mTLS_verified"] = True
    report["mtls_scope"] = "HOSTED_EPHEMERAL_ACTUAL_TLS_SOCKETS_ONLY"
    report["production_mTLS_verified"] = False
    report["tenant_isolation_verified"] = False
    report["production_verified"] = False
    report["status"] = "PASS"
    args.report.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("XERJ_REAL_GATEWAY_BACKEND_MTLS_LIVE_PASS checks="+str(len(report["checks"])))

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Never print the error details or raw HTTP response/credentials.
        print("XERJ_REAL_MTLS_LIVE_HOLD reason="+type(error).__name__,file=sys.stderr)
        raise SystemExit(1)
