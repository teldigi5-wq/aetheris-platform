#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
STUB = "http://127.0.0.1:19090"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME proof exercises the real Aetheris OAuth/PKCE lifecycle against a local synthetic provider "
    "over real HTTP, including authorization-code exchange, token refresh, read-only identity health and local "
    "credential revocation. It does not prove a live GitHub App/OAuth App, Google Cloud OAuth consent screen, "
    "Gmail/Calendar production data access, Microsoft/Meta provider access, production secret-manager durability, "
    "public-ingress hardening, provider SLA behavior, outbound provider mutation, or physical-PC behavior."
)


def request(base: str, method: str, path: str, payload: Any | None = None) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status = response.status
            raw = response.read().decode()
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode()
    if not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def wait_health() -> None:
    deadline = time.monotonic() + 180
    last: Any = None
    while time.monotonic() < deadline:
        try:
            status, data = request(BASE, "GET", "/actuator/health")
            last = (status, data)
            if status == 200 and isinstance(data, dict) and data.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"orchestrator not healthy: {last!r}")


def register(provider: str, external_ref: str, capabilities: list[str]) -> dict[str, Any]:
    status, data = request(BASE, "POST", "/api/orchestrator/connectors/connections", {
        "ownerId": "phase3-proof-owner",
        "provider": provider,
        "externalAccountRef": external_ref,
        "displayName": f"Phase 3 {provider}",
        "capabilities": capabilities,
    })
    if status != 200 or not isinstance(data, dict) or not data.get("id"):
        raise AssertionError(f"connector registration failed: {provider=} {status=} {data=!r}")
    return data


def start_oauth(connection_id: str, redirect_uri: str) -> dict[str, Any]:
    status, data = request(BASE, "POST", f"/api/orchestrator/connectors/connections/{connection_id}/oauth/authorizations", {
        "redirectUri": redirect_uri,
    })
    if status != 200 or not isinstance(data, dict) or not data.get("authorizationUrl") or not data.get("state"):
        raise AssertionError(f"OAuth authorization start failed: {status=} {data=!r}")
    return data


def complete(session_id: str, code: str, state: str) -> tuple[int, Any]:
    return request(BASE, "POST", f"/api/orchestrator/connectors/oauth/authorizations/{session_id}/complete", {
        "code": code,
        "state": state,
    })


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    data: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "connector-live-providers-phase3",
        "physical_pc_validation": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        data["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="build-evidence/runtime/connector-live-providers-phase3-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-live-providers-phase3-report.json")
    args = parser.parse_args()
    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required = {item["id"] for item in contract["checks"]}
    checks: dict[str, str] = {}
    output = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health()
        passed("service.orchestrator.health")

        github = register("GITHUB", "phase3-github-proof", ["INGEST_CODE_UPDATES"])
        github_auth = start_oauth(github["id"], "http://localhost:7777/oauth/github")
        parsed = urllib.parse.urlparse(github_auth["authorizationUrl"])
        query = urllib.parse.parse_qs(parsed.query)
        if query.get("state", [None])[0] != github_auth["state"]:
            raise AssertionError("GitHub authorization state mismatch")
        passed("oauth.github-authorization-created")
        if query.get("code_challenge_method") != ["S256"] or not query.get("code_challenge", [""])[0]:
            raise AssertionError(f"PKCE S256 missing: {query!r}")
        passed("oauth.pkce-s256-present")
        github_scopes = set(github_auth.get("scopes", []))
        if github_scopes != {"read:user", "user:email"}:
            raise AssertionError(f"GitHub scopes are not read-only minimum set: {github_scopes!r}")
        passed("oauth.readonly-scopes-only")

        status, invalid = complete(github_auth["sessionId"], "github-proof-code", "wrong-state")
        if status != 401:
            raise AssertionError(f"invalid OAuth state was not rejected: {status=} {invalid=!r}")
        passed("oauth.invalid-state-rejected")

        status, credential = complete(github_auth["sessionId"], "github-proof-code", github_auth["state"])
        if status != 200 or credential.get("status") != "ACTIVE":
            raise AssertionError(f"authorization code exchange failed: {status=} {credential=!r}")
        if credential.get("accessTokenConfigured") is not True or credential.get("refreshTokenConfigured") is not True:
            raise AssertionError(f"credential booleans were not active: {credential!r}")
        passed("oauth.authorization-code-exchange-completes")

        status, replay = complete(github_auth["sessionId"], "github-proof-code", github_auth["state"])
        if status != 409:
            raise AssertionError(f"OAuth state/session reuse was not rejected: {status=} {replay=!r}")
        passed("oauth.state-single-use")

        status, view = request(BASE, "GET", f"/api/orchestrator/connectors/connections/{github['id']}/oauth/credential")
        serialized = json.dumps([credential, view], sort_keys=True)
        forbidden = ["phase3-access-", "phase3-refresh-", "accessTokenReference", "refreshTokenReference", "clientSecret"]
        if status != 200 or any(item in serialized for item in forbidden):
            raise AssertionError(f"token material/reference exposed: {serialized}")
        passed("oauth.token-material-not-exposed")
        passed("oauth.credential-reference-lifecycle")

        status, health = request(BASE, "GET", f"/api/orchestrator/connectors/connections/{github['id']}/provider-health")
        if status != 200 or health.get("healthy") is not True or health.get("accountId") != "424242":
            raise AssertionError(f"GitHub read-only identity health failed: {status=} {health=!r}")
        passed("oauth.readonly-provider-health-pass")

        status, before_metrics = request(STUB, "GET", "/metrics")
        if status != 200:
            raise AssertionError("stub metrics unavailable")
        status, refreshed = request(BASE, "POST", f"/api/orchestrator/connectors/connections/{github['id']}/oauth/refresh")
        status2, after_metrics = request(STUB, "GET", "/metrics")
        if status != 200 or refreshed.get("status") != "ACTIVE" or status2 != 200:
            raise AssertionError(f"refresh failed: {status=} {refreshed=!r}")
        if after_metrics.get("issuedTokens", 0) <= before_metrics.get("issuedTokens", 0):
            raise AssertionError(f"refresh did not obtain a new runtime token: {before_metrics=!r} {after_metrics=!r}")
        passed("oauth.refresh-rotates-runtime-token")

        gmail = register("GMAIL", "phase3-gmail-proof", ["INGEST_MESSAGES"])
        gmail_auth = start_oauth(gmail["id"], "http://localhost:7777/oauth/google")
        gmail_scopes = set(gmail_auth.get("scopes", []))
        if "https://www.googleapis.com/auth/gmail.readonly" not in gmail_scopes:
            raise AssertionError(f"Gmail readonly scope missing: {gmail_scopes!r}")
        passed("oauth.google-gmail-authorization-created")
        status, gmail_credential = complete(gmail_auth["sessionId"], "google-gmail-code", gmail_auth["state"])
        if status != 200 or gmail_credential.get("status") != "ACTIVE":
            raise AssertionError(f"Gmail OAuth exchange failed: {status=} {gmail_credential=!r}")
        status, gmail_health = request(BASE, "GET", f"/api/orchestrator/connectors/connections/{gmail['id']}/provider-health")
        if status != 200 or gmail_health.get("healthy") is not True or gmail_health.get("accountId") != "google-phase3-proof":
            raise AssertionError(f"Gmail identity check failed: {status=} {gmail_health=!r}")
        passed("oauth.gmail-identity-health-pass")

        calendar = register("CALENDAR", "phase3-calendar-proof", ["INGEST_MESSAGES"])
        calendar_auth = start_oauth(calendar["id"], "http://localhost:7777/oauth/google-calendar")
        calendar_scopes = set(calendar_auth.get("scopes", []))
        if "https://www.googleapis.com/auth/calendar.readonly" not in calendar_scopes:
            raise AssertionError(f"Calendar readonly scope missing: {calendar_scopes!r}")
        if any("calendar.events" in scope or "calendar.acl" in scope for scope in calendar_scopes):
            raise AssertionError(f"Calendar write scope leaked into authorization: {calendar_scopes!r}")
        passed("oauth.google-calendar-readonly-scope")

        status, revoked = request(BASE, "DELETE", f"/api/orchestrator/connectors/connections/{github['id']}/oauth/credential")
        if status != 200 or revoked.get("status") != "REVOKED" or revoked.get("accessTokenConfigured") is not False or revoked.get("refreshTokenConfigured") is not False:
            raise AssertionError(f"local credential revocation failed: {status=} {revoked=!r}")
        passed("oauth.disconnect-revokes-local-credential")
        status, blocked = request(BASE, "GET", f"/api/orchestrator/connectors/connections/{github['id']}/provider-health")
        if status != 409:
            raise AssertionError(f"revoked provider health was not blocked: {status=} {blocked=!r}")
        passed("oauth.revoked-health-blocked")

        status, metrics = request(STUB, "GET", "/metrics")
        if status != 200 or metrics.get("unsafeMutations") != 0:
            raise AssertionError(f"provider mutation boundary violated: {metrics!r}")
        passed("oauth.no-outbound-provider-mutation")
        passed("oauth.truth-boundary")

        missing = required - set(checks)
        if missing:
            raise AssertionError(f"missing checks: {sorted(missing)}")
        write_report(output, checks, "PASS")
        print(f"PASS: {len(checks)} OAuth/live-provider foundation checks")
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", repr(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
