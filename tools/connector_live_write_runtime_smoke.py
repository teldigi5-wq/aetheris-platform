#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
STUB = "http://127.0.0.1:19090"
EVIDENCE_CLASS = "HOSTED_RUNTIME_PROVIDER_WRITE_STUB"
CAPABILITY = "connector-live-write-phase7"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME_PROVIDER_WRITE_STUB proves owner-approved LIVE-mode connector actions perform real HTTP "
    "POST requests using runtime OAuth credentials against a local synthetic provider stub. It does not prove "
    "mutation of production Gmail, Google Calendar or GitHub accounts, provider-side exactly-once guarantees, "
    "production secret-manager durability, unattended production autonomy, or physical-PC validation."
)


def request(base: str, method: str, path: str, payload: Any | None = None, timeout: int = 20) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
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
            status, data = request(BASE, "GET", "/actuator/health", timeout=5)
            last = (status, data)
            if status == 200 and isinstance(data, dict) and data.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"orchestrator not healthy: {last!r}")


def register(provider: str, external_ref: str, capabilities: list[str]) -> dict[str, Any]:
    status, connection = request(BASE, "POST", "/api/orchestrator/connectors/connections", {
        "ownerId": "phase7-runtime-owner",
        "provider": provider,
        "externalAccountRef": external_ref,
        "displayName": f"Phase 7 {provider}",
        "capabilities": capabilities,
    })
    if status != 200 or not isinstance(connection, dict) or not connection.get("id"):
        raise AssertionError(f"connector registration failed: provider={provider} status={status} body={connection!r}")
    return connection


def authorize(connection: dict[str, Any], suffix: str) -> dict[str, Any]:
    connection_id = connection["id"]
    status, auth = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/connections/{connection_id}/oauth/authorizations",
        {"redirectUri": f"http://localhost:7777/oauth/phase7-{suffix}"},
    )
    if status != 200 or not isinstance(auth, dict) or not auth.get("sessionId") or not auth.get("state"):
        raise AssertionError(f"OAuth start failed: provider={connection.get('provider')} status={status} body={auth!r}")
    status, credential = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/oauth/authorizations/{auth['sessionId']}/complete",
        {"code": f"phase7-{suffix}-code", "state": auth["state"]},
    )
    if status != 200 or not isinstance(credential, dict) or credential.get("status") != "ACTIVE":
        raise AssertionError(f"OAuth completion failed: status={status} body={credential!r}")
    if credential.get("accessTokenConfigured") is not True:
        raise AssertionError(f"OAuth access token reference was not configured: {credential!r}")
    return credential


def create_live_action(connection_id: str, kind: str, key: str, target: str, summary: str) -> dict[str, Any]:
    status, action = request(BASE, "POST", "/api/orchestrator/connectors/actions", {
        "connectionId": connection_id,
        "actionKind": kind,
        "idempotencyKey": key,
        "targetRef": target,
        "summary": summary,
        "executionMode": "LIVE",
    })
    if status != 200 or not isinstance(action, dict) or not action.get("id"):
        raise AssertionError(f"live action creation failed: kind={kind} status={status} body={action!r}")
    if action.get("status") != "PENDING_APPROVAL" or not action.get("approvalId") or not action.get("taskId"):
        raise AssertionError(f"live action was not approval-gated: {action!r}")
    return action


def approve(action: dict[str, Any]) -> None:
    status, decision = request(
        BASE,
        "POST",
        f"/api/orchestrator/approvals/{action['approvalId']}/decision",
        {"approved": True, "note": "Phase 7 hosted provider-write stub approval"},
    )
    if status != 200 or not isinstance(decision, dict) or decision.get("status") != "APPROVED":
        raise AssertionError(f"approval failed: status={status} body={decision!r}")


def execute(action: dict[str, Any]) -> dict[str, Any]:
    status, result = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{action['id']}/execute")
    if status != 200 or not isinstance(result, dict):
        raise AssertionError(f"live execution failed: status={status} body={result!r}")
    if result.get("status") != "EXECUTED" or result.get("replay") is not False or not result.get("executedAt"):
        raise AssertionError(f"live execution receipt is incomplete: {result!r}")
    return result


def metrics() -> dict[str, Any]:
    status, data = request(STUB, "GET", "/metrics")
    if status != 200 or not isinstance(data, dict):
        raise AssertionError(f"provider stub metrics unavailable: status={status} body={data!r}")
    return data


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    data: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": EVIDENCE_CLASS,
        "capability": CAPABILITY,
        "environment": "github-hosted-ubuntu-docker-compose-local-provider-stub",
        "physical_pc_validation": False,
        "production_provider_mutation_proven": False,
        "provider_exactly_once_proven": False,
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
    parser.add_argument("--contract", default="build-evidence/runtime/connector-live-write-phase7-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-live-write-phase7-report.json")
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

        status, policy = request(BASE, "GET", "/api/orchestrator/connectors/actions/policy")
        if status != 200 or not isinstance(policy, dict):
            raise AssertionError(f"connector action policy unavailable: status={status} body={policy!r}")
        if policy.get("liveWritesEnabled") is not True or policy.get("ownerApprovalRequired") is not True:
            raise AssertionError(f"Phase 7 proof runtime did not explicitly enable guarded live writes: {policy!r}")
        passed("live.policy-enabled-in-proof-runtime")

        github = register("GITHUB", "phase7-github-proof", ["INGEST_CODE_UPDATES"])
        github_credential = authorize(github, "github")
        if "public_repo" not in set(github_credential.get("scopes") or []):
            raise AssertionError(f"GitHub write scope not issued in Phase 7 proof runtime: {github_credential!r}")
        passed("oauth.github-write-scope-issued")

        gmail = register("GMAIL", "phase7-gmail-proof", ["INGEST_MESSAGES", "DRAFT_REPLIES"])
        gmail_credential = authorize(gmail, "gmail")
        if "https://www.googleapis.com/auth/gmail.send" not in set(gmail_credential.get("scopes") or []):
            raise AssertionError(f"Gmail write scope not issued in Phase 7 proof runtime: {gmail_credential!r}")
        passed("oauth.gmail-write-scope-issued")

        calendar = register("CALENDAR", "phase7-calendar-proof", ["READ_CALENDAR"])
        calendar_credential = authorize(calendar, "calendar")
        if "https://www.googleapis.com/auth/calendar.events" not in set(calendar_credential.get("scopes") or []):
            raise AssertionError(f"Calendar write scope not issued in Phase 7 proof runtime: {calendar_credential!r}")
        passed("oauth.calendar-write-scope-issued")

        gmail_action = create_live_action(
            gmail["id"],
            "GMAIL_SEND_EMAIL",
            "phase7-gmail-live-001",
            "phase7-recipient@example.test",
            "Phase 7 approved Gmail live-write proof",
        )
        calendar_action = create_live_action(
            calendar["id"],
            "CALENDAR_CREATE_EVENT",
            "phase7-calendar-live-001",
            "primary|2026-09-20T09:00:00Z|2026-09-20T10:00:00Z",
            "Phase 7 approved calendar event",
        )
        github_action = create_live_action(
            github["id"],
            "GITHUB_CREATE_ISSUE",
            "phase7-github-live-001",
            "proof/aetheris-platform",
            "Phase 7 approved GitHub issue",
        )

        for action, check_id in (
            (gmail_action, "live.gmail-unapproved-blocked"),
            (calendar_action, "live.calendar-unapproved-blocked"),
            (github_action, "live.github-unapproved-blocked"),
        ):
            status, blocked = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{action['id']}/execute")
            if status != 409:
                raise AssertionError(f"unapproved live action reached execution: status={status} body={blocked!r}")
            passed(check_id)

        before = metrics()
        if before.get("writeMutations") != 0 or before.get("unsafeMutations") != 0:
            raise AssertionError(f"provider mutation occurred before owner approvals: {before!r}")
        passed("live.provider-untouched-before-approval")

        for action in (gmail_action, calendar_action, github_action):
            approve(action)

        gmail_result = execute(gmail_action)
        if not str(gmail_result.get("externalReference", "")).startswith("gmail:gmail-write-"):
            raise AssertionError(f"Gmail provider receipt missing: {gmail_result!r}")
        passed("live.gmail-approved-executes")

        calendar_result = execute(calendar_action)
        if not str(calendar_result.get("externalReference", "")).startswith("calendar:"):
            raise AssertionError(f"Calendar provider receipt missing: {calendar_result!r}")
        passed("live.calendar-approved-executes")

        github_result = execute(github_action)
        if "github.example.test/proof/aetheris-platform/issues/" not in str(github_result.get("externalReference", "")):
            raise AssertionError(f"GitHub provider receipt missing: {github_result!r}")
        passed("live.github-approved-executes")

        after = metrics()
        if after.get("gmailWrites") != 1:
            raise AssertionError(f"Gmail POST was not observed exactly once by stub: {after!r}")
        passed("live.gmail-provider-post-observed")
        if after.get("calendarWrites") != 1:
            raise AssertionError(f"Calendar POST was not observed exactly once by stub: {after!r}")
        passed("live.calendar-provider-post-observed")
        if after.get("githubWrites") != 1:
            raise AssertionError(f"GitHub POST was not observed exactly once by stub: {after!r}")
        passed("live.github-provider-post-observed")
        if after.get("writeMutations") != 3 or after.get("unsafeMutations") != 0:
            raise AssertionError(f"provider stub mutation accounting is unsafe: {after!r}")
        passed("live.provider-no-unsafe-mutations")

        status, replay = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{gmail_action['id']}/execute")
        if status != 200 or not isinstance(replay, dict) or replay.get("replay") is not True:
            raise AssertionError(f"live execution replay was not idempotent locally: status={status} body={replay!r}")
        if replay.get("externalReference") != gmail_result.get("externalReference"):
            raise AssertionError(f"live replay changed persisted provider receipt: {replay!r}")
        replay_metrics = metrics()
        if replay_metrics.get("writeMutations") != 3:
            raise AssertionError(f"live replay issued a second provider POST: {replay_metrics!r}")
        passed("live.execution-replay-idempotent")

        status, actions = request(BASE, "GET", "/api/orchestrator/connectors/actions")
        if status != 200 or not isinstance(actions, list):
            raise AssertionError(f"connector action history unavailable: status={status} body={actions!r}")
        proof_ids = {gmail_action["id"], calendar_action["id"], github_action["id"]}
        persisted = [item for item in actions if item.get("id") in proof_ids]
        if len(persisted) != 3 or any(
            item.get("status") != "EXECUTED" or item.get("executionMode") != "LIVE" or not item.get("externalReference")
            for item in persisted
        ):
            raise AssertionError(f"live action receipts not persisted: {persisted!r}")
        passed("live.receipts-persisted")

        for item in persisted:
            status, approval_rows = request(BASE, "GET", f"/api/orchestrator/approvals/task/{item['taskId']}")
            if status != 200 or not isinstance(approval_rows, list) or not any(
                row.get("status") == "APPROVED" and str(row.get("id")) == str(item.get("approvalId"))
                for row in approval_rows
            ):
                raise AssertionError(f"live action lost owner-approval audit link: {item!r} approvals={approval_rows!r}")
        passed("live.approval-audit-preserved")

        if contract.get("truth_boundary") != TRUTH_BOUNDARY:
            raise AssertionError("Phase 7 contract truth boundary differs from runtime harness")
        passed("live.truth-boundary")

        missing = sorted(required - set(checks))
        extra = sorted(set(checks) - required)
        if missing or extra:
            raise AssertionError(f"contract mismatch missing={missing} extra={extra}")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks), "provider_posts": 3}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
