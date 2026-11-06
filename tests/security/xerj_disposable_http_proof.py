#!/usr/bin/env python3
"""Live security proof using real localhost Gateway, Identity, User, Audit,
PostgreSQL, Redis and RabbitMQ. No production credentials, data or endpoints.
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

SERVICES = {
    "gateway": 8080, "identity": 8082, "user": 8081, "audit": 8083
}
CHECKS: dict[str, str] = {}
def verify(condition, name):
    if not condition:
        raise AssertionError("XERJ_DISPOSABLE_CHECK_FAILED " + name)
    CHECKS[name] = "PASS"

def request(port, method, path, payload=None, bearer=None, extra=None):
    url = f"http://127.0.0.1:{port}{path}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if bearer is not None:
        headers["Authorization"] = "Bearer " + bearer
    if extra:
        headers.update(extra)
    req = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(req, timeout=6) as response:
            status = response.status
            raw = response.read(200_001)
    except HTTPError as error:
        status = error.code
        raw = error.read(200_001)
    # Never log raw responses: they may contain JWTs, refresh tokens or PII.
    if len(raw) > 200_000:
        raise AssertionError("oversized response")
    try:
        document = json.loads(raw) if raw else None
    except json.JSONDecodeError:
        document = None
    return status, document

def healthcheck():
    deadline = time.monotonic() + 135
    missing = set(SERVICES)
    while missing and time.monotonic() < deadline:
        for svc in tuple(missing):
            try:
                status, doc = request(SERVICES[svc], "GET", "/actuator/health")
                if status == 200 and isinstance(doc, dict) and doc.get("status") == "UP":
                    missing.remove(svc)
            except (URLError, OSError, TimeoutError):
                pass
        if missing:
            time.sleep(2)
    verify(not missing, "all_four_real_java_services_healthy")

def synthetic_admin_bootstrap(compose):
    # Changes ONLY the new disposable PostgreSQL container's synthetic account.
    # The static identifier is supplied here, not derived from user input.
    cmd = [
        "docker", "compose", "-p", os.environ["XERJ_COMPOSE_PROJECT"],
        "-f", compose, "exec", "-T", "postgres", "psql",
        "-X", "-v", "ON_ERROR_STOP=1", "-U", "xerj_ci", "-d", "xerj_ci",
        "-t", "-A", "-c",
        "UPDATE identity_accounts SET role='ADMIN' WHERE email='e2e-admin@aetheris.test' RETURNING id;"
    ]
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=15, check=False)
    verify(p.returncode == 0 and len([v for v in p.stdout.splitlines()
        if v.strip().isdigit()]) == 1, "synthetic_admin_bootstrap_single_row")

def registration(name, email):
    status, obj = request(8080, "POST", "/api/auth/register",
        {"name": name, "email": email, "password": "SyntheticPass123!Unique"})
    verify(status == 201 and isinstance(obj, dict) and
           isinstance(obj.get("accessToken"), str) and obj.get("account", {}).get("role") == "API_CONSUMER",
           "identity_registration_via_real_gateway_" + name.lower())
    return obj["accessToken"]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--compose", required=True)
    args = parser.parse_args()
    healthcheck()
    consumer = registration("Consumer", "e2e-consumer@aetheris.test")
    registration("Admin", "e2e-admin@aetheris.test")
    synthetic_admin_bootstrap(args.compose)
    status, obj = request(8080, "POST", "/api/auth/login", {
        "email": "e2e-admin@aetheris.test", "password": "SyntheticPass123!Unique"
    })
    verify(status == 200 and isinstance(obj, dict) and
           obj.get("account", {}).get("role") == "ADMIN" and
           isinstance(obj.get("accessToken"), str), "real_identity_signs_admin_token")
    admin = obj["accessToken"]

    for service, port, path in (("user", 8081, "/api/users"),
                                ("audit", 8083, "/api/events")):
        code, _ = request(port, "GET", path)
        verify(code == 401, service + "_direct_anonymous_denied")
        code, _ = request(port, "GET", path,
            extra={"X-Aetheris-User-Role": "ADMIN", "X-Aetheris-User-Id": "1"})
        verify(code == 401, service + "_forged_internal_headers_denied")
        code, _ = request(port, "GET", path, bearer=consumer)
        verify(code == 403, service + "_direct_consumer_denied")
        code, _ = request(8080, "GET", path, bearer=consumer)
        verify(code == 403, service + "_gateway_consumer_denied")
        code, _ = request(port, "GET", path, bearer=admin)
        verify(code == 200, service + "_direct_authorized_admin")

    code, _ = request(8080, "GET", "/api/users")
    verify(code == 401, "gateway_anonymous_user_read_denied")
    code, _ = request(8080, "GET", "/api/users", bearer="malformed.synthetic.token")
    verify(code == 401, "gateway_malformed_token_denied")
    code, _ = request(8080, "GET", "/api/users",
        extra={"X-Aetheris-User-Role": "ADMIN", "X-Aetheris-User-Id": "1"})
    verify(code == 401, "gateway_forged_headers_denied")
    code, _ = request(8080, "GET", "/api/users", bearer=admin)
    verify(code == 200, "gateway_valid_admin_read_succeeds")
    code, obj = request(8080, "POST", "/api/users", {
        "name": "Synthetic User", "email": "e2e-user@aetheris.test"
    }, bearer=admin)
    verify(code == 201 and isinstance(obj, dict) and
           isinstance(obj.get("id"), int), "gateway_real_postgres_admin_user_create")
    user_id = obj["id"]
    code, _ = request(8080, "GET", f"/api/users/{user_id}", bearer=consumer)
    verify(code == 403, "gateway_consumer_cross_user_read_denied")
    code, data = request(8080, "GET", f"/api/users/{user_id}", bearer=admin)
    verify(code == 200 and data and data.get("id") == user_id,
           "gateway_admin_object_read")

    found_created = False
    for _ in range(20):
        code, events = request(8080, "GET", "/api/events", bearer=admin)
        if code == 200 and isinstance(events, list) and any(
            item.get("eventType") == "user.created" and item.get("userId") == user_id
            for item in events if isinstance(item, dict)):
            found_created = True
            break
        time.sleep(1)
    verify(found_created, "rabbitmq_user_created_reaches_real_audit_postgres")

    code, _ = request(8080, "DELETE", f"/api/users/{user_id}", bearer=consumer)
    verify(code == 403, "gateway_consumer_user_delete_denied")
    code, _ = request(8080, "DELETE", f"/api/users/{user_id}", bearer=admin)
    verify(code == 204, "gateway_admin_user_delete")

    report = {
        "status": "PASS",
        "source_commit": os.environ["GITHUB_SHA"],
        "evidence_class": "HOSTED_DISPOSABLE_INTEGRATION",
        "production_verified": False,
        "tenant_isolation_verified": False,
        "ci_only_synthetic_bootstrap": True,
        "checks": CHECKS,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("XERJ_LIVE_BOUNDARY_CHECKS_PASS count=" + str(len(CHECKS)))

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # Do not disclose exception details that may contain access tokens or HTTP responses.
        print("XERJ_LIVE_BOUNDARY_FAILED reason=" + type(exc).__name__)
        raise SystemExit(1)
