#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
BROKER = "http://127.0.0.1:19091"
EVIDENCE_CLASS = "LIVE_PROVIDER_TEST_ACCOUNT_WRITES"
CAPABILITY = "connector-live-write-phase8"
ARM_VALUE = "I_UNDERSTAND_PHASE8_CREATES_LIVE_TEST_DATA"
TRUTH_BOUNDARY = (
    "LIVE_PROVIDER_TEST_ACCOUNT_WRITES proves that explicitly armed, owner-approved Aetheris connector actions "
    "can create test data through the real Gmail, Google Calendar, and GitHub provider APIs using repository-scoped "
    "test-account credentials. Evidence is sanitized and does not persist tokens, provider response bodies, account "
    "identifiers, message/event/issue identifiers, or target values. It does not prove provider-side exactly-once "
    "guarantees, safety for production accounts, production secret-manager durability, unattended production "
    "autonomy, or physical-PC validation."
)
CHECK_IDS = (
    "gate.confirmation",
    "gate.credentials-present",
    "gate.targets-present",
    "gate.project-repo-blocked",
    "service.orchestrator.health",
    "live.policy-enabled",
    "oauth.github-write-scope-issued",
    "oauth.gmail-write-scope-issued",
    "oauth.calendar-write-scope-issued",
    "live.github-unapproved-blocked",
    "live.gmail-unapproved-blocked",
    "live.calendar-unapproved-blocked",
    "live.github-approved-executes",
    "live.gmail-approved-executes",
    "live.calendar-approved-executes",
    "live.execution-replay-idempotent",
    "live.receipts-persisted",
    "live.approval-audit-preserved",
    "live.evidence-sanitized",
    "live.truth-boundary",
)
EMAIL = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
REPOSITORY = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
PROVIDER_HTTP_STATUS = re.compile(r"\bHTTP\s+(\d{3})\b")


class Phase8ValidationError(AssertionError):
    def __init__(
        self,
        stage: str,
        message: str,
        *,
        orchestrator_http_status: int | None = None,
        provider_http_status: int | None = None,
    ) -> None:
        super().__init__(message)
        self.stage = stage
        self.orchestrator_http_status = orchestrator_http_status
        self.provider_http_status = provider_http_status


def request(base: str, method: str, path: str, payload: Any | None = None, timeout: int = 20) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.status
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8")
    if not raw.strip():
        return status, None
    try:
        return status, json.loads(raw)
    except json.JSONDecodeError:
        return status, raw


def safe_provider_status(payload: Any) -> int | None:
    """Extract only a provider HTTP status code; never persist the response payload itself."""
    try:
        rendered = payload if isinstance(payload, str) else json.dumps(payload, separators=(",", ":"))
    except (TypeError, ValueError):
        return None
    match = PROVIDER_HTTP_STATUS.search(rendered)
    return int(match.group(1)) if match else None


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise AssertionError(f"required Phase 8 environment value is missing: {name}")
    return value


def wait_health() -> None:
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        try:
            status, data = request(BASE, "GET", "/actuator/health", timeout=5)
            if status == 200 and isinstance(data, dict) and data.get("status") == "UP":
                return
        except Exception:  # noqa: BLE001
            pass
        time.sleep(2)
    raise AssertionError("orchestrator did not become healthy")


def broker_ready() -> bool:
    status, data = request(BROKER, "GET", "/health", timeout=5)
    return bool(
        status == 200
        and isinstance(data, dict)
        and data.get("status") == "UP"
        and data.get("githubConfigured") is True
        and data.get("gmailConfigured") is True
        and data.get("calendarConfigured") is True
    )


def register(provider: str, suffix: str, capabilities: list[str]) -> dict[str, Any]:
    status, connection = request(BASE, "POST", "/api/orchestrator/connectors/connections", {
        "ownerId": "phase8-live-test-owner",
        "provider": provider,
        "externalAccountRef": f"phase8-{suffix}-test-account",
        "displayName": f"Phase 8 {provider} Test Account",
        "capabilities": capabilities,
    })
    if status != 200 or not isinstance(connection, dict) or not connection.get("id"):
        raise AssertionError("connector registration failed")
    return connection


def authorize(connection: dict[str, Any], suffix: str) -> dict[str, Any]:
    connection_id = connection["id"]
    status, auth = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/connections/{connection_id}/oauth/authorizations",
        {"redirectUri": f"http://localhost:7777/oauth/phase8-{suffix}"},
    )
    if status != 200 or not isinstance(auth, dict) or not auth.get("sessionId") or not auth.get("state"):
        raise AssertionError("OAuth authorization start failed")
    status, credential = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/oauth/authorizations/{auth['sessionId']}/complete",
        {"code": f"phase8-{suffix}-code", "state": auth["state"]},
    )
    if status != 200 or not isinstance(credential, dict) or credential.get("status") != "ACTIVE":
        raise AssertionError("OAuth authorization completion failed")
    if credential.get("accessTokenConfigured") is not True:
        raise AssertionError("OAuth access token was not loaded into the runtime credential vault")
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
        raise AssertionError("live action creation failed")
    if action.get("status") != "PENDING_APPROVAL" or not action.get("approvalId") or not action.get("taskId"):
        raise AssertionError("live action did not enter the mandatory owner-approval gate")
    return action


def approve(action: dict[str, Any]) -> None:
    status, decision = request(
        BASE,
        "POST",
        f"/api/orchestrator/approvals/{action['approvalId']}/decision",
        {"approved": True, "note": "Phase 8 credential-gated live test-account validation"},
    )
    if status != 200 or not isinstance(decision, dict) or decision.get("status") != "APPROVED":
        raise AssertionError("owner approval failed")


def execute(action: dict[str, Any], provider: str) -> dict[str, Any]:
    status, result = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{action['id']}/execute", timeout=30)
    if status != 200:
        raise Phase8ValidationError(
            f"{provider}.orchestrator-execute-http",
            "live provider execution failed",
            orchestrator_http_status=status,
            provider_http_status=safe_provider_status(result),
        )
    if not isinstance(result, dict):
        raise Phase8ValidationError(
            f"{provider}.orchestrator-response-shape",
            "live provider execution returned a non-object receipt",
            orchestrator_http_status=status,
        )
    if result.get("status") != "EXECUTED" or result.get("replay") is not False or not result.get("executedAt"):
        raise Phase8ValidationError(
            f"{provider}.execution-receipt",
            "live execution receipt is incomplete",
            orchestrator_http_status=status,
        )
    return result


def report_payload(
    checks: dict[str, str],
    status: str,
    error_type: str | None = None,
    *,
    failure_stage: str | None = None,
    orchestrator_http_status: int | None = None,
    provider_http_status: int | None = None,
) -> dict[str, Any]:
    data: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": EVIDENCE_CLASS,
        "capability": CAPABILITY,
        "environment": "github-hosted-ubuntu-docker-compose-real-test-provider-apis",
        "physical_pc_validation": False,
        "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
        "provider_mutations_attempted": ["gmail.send", "calendar.events.create", "github.issues.create"],
        "provider_response_bodies_persisted": False,
        "account_identifiers_persisted": False,
        "target_values_persisted": False,
        "token_material_persisted": False,
        "provider_exactly_once_proven": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if error_type:
        data["error_type"] = error_type
    if failure_stage:
        data["failure_stage"] = failure_stage
    if orchestrator_http_status is not None:
        data["orchestrator_http_status"] = int(orchestrator_http_status)
    if provider_http_status is not None:
        data["provider_http_status"] = int(provider_http_status)
    return data


def write_report(
    path: Path,
    checks: dict[str, str],
    status: str,
    error_type: str | None = None,
    *,
    failure_stage: str | None = None,
    orchestrator_http_status: int | None = None,
    provider_http_status: int | None = None,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = report_payload(
        checks,
        status,
        error_type,
        failure_stage=failure_stage,
        orchestrator_http_status=orchestrator_http_status,
        provider_http_status=provider_http_status,
    )
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def validate_contract(contract: dict[str, Any]) -> None:
    declared = tuple(item["id"] for item in contract.get("checks", []))
    if declared != CHECK_IDS:
        raise AssertionError("Phase 8 contract check list differs from validator")
    if contract.get("capability") != CAPABILITY or contract.get("evidence_class") != EVIDENCE_CLASS:
        raise AssertionError("Phase 8 contract identity differs from validator")
    if contract.get("truth_boundary") != TRUTH_BOUNDARY:
        raise AssertionError("Phase 8 contract truth boundary differs from validator")
    if contract.get("arm_confirmation") != ARM_VALUE:
        raise AssertionError("Phase 8 arm confirmation differs from validator")
    if contract.get("physical_pc_status") != "BLOCKED_PENDING_HARDWARE":
        raise AssertionError("Phase 8 must preserve the physical-PC blocked status")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="build-evidence/runtime/connector-live-write-phase8-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-live-write-phase8-report.json")
    parser.add_argument("--preflight", action="store_true")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    validate_contract(contract)
    if args.preflight:
        print(json.dumps({"status": "PASS", "capability": CAPABILITY, "checks": len(CHECK_IDS)}))
        return 0

    checks: dict[str, str] = {}
    output = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in CHECK_IDS:
            raise AssertionError("validator attempted to record an undeclared check")
        checks[check_id] = "PASS"

    try:
        if required_env("AETHERIS_PHASE8_ARM_CONFIRMATION") != ARM_VALUE:
            raise AssertionError("Phase 8 live test-account mutation lane is not explicitly armed")
        passed("gate.confirmation")

        if not broker_ready():
            raise AssertionError("Phase 8 credential broker is not ready with all three provider credentials")
        passed("gate.credentials-present")

        gmail_target = required_env("AETHERIS_PHASE8_TEST_GMAIL_RECIPIENT")
        calendar_id = required_env("AETHERIS_PHASE8_TEST_CALENDAR_ID")
        github_repo = required_env("AETHERIS_PHASE8_TEST_GITHUB_REPOSITORY")
        if not EMAIL.fullmatch(gmail_target):
            raise AssertionError("Phase 8 Gmail target is not a valid email address")
        if not REPOSITORY.fullmatch(github_repo):
            raise AssertionError("Phase 8 GitHub target is not owner/repository")
        if not calendar_id or len(calendar_id) > 240:
            raise AssertionError("Phase 8 Calendar target is invalid")
        passed("gate.targets-present")

        forbidden_repo = str(contract.get("forbidden_github_target", "")).strip().lower()
        if github_repo.strip().lower() == forbidden_repo:
            raise AssertionError("Phase 8 refuses to create validation issues in the Aetheris project repository")
        passed("gate.project-repo-blocked")

        wait_health()
        passed("service.orchestrator.health")

        status, policy = request(BASE, "GET", "/api/orchestrator/connectors/actions/policy")
        if status != 200 or not isinstance(policy, dict):
            raise AssertionError("connector action policy is unavailable")
        if policy.get("liveWritesEnabled") is not True or policy.get("ownerApprovalRequired") is not True:
            raise AssertionError("guarded live writes are not explicitly enabled with owner approval")
        passed("live.policy-enabled")

        github = register("GITHUB", "github", ["INGEST_CODE_UPDATES"])
        gmail = register("GMAIL", "gmail", ["INGEST_MESSAGES", "DRAFT_REPLIES"])
        calendar = register("CALENDAR", "calendar", ["READ_CALENDAR"])

        github_credential = authorize(github, "github")
        if not ({"public_repo", "repo", "issues:write"} & set(github_credential.get("scopes") or [])):
            raise AssertionError("GitHub write scope was not issued")
        passed("oauth.github-write-scope-issued")

        gmail_credential = authorize(gmail, "gmail")
        if "https://www.googleapis.com/auth/gmail.send" not in set(gmail_credential.get("scopes") or []):
            raise AssertionError("Gmail send scope was not issued")
        passed("oauth.gmail-write-scope-issued")

        calendar_credential = authorize(calendar, "calendar")
        if "https://www.googleapis.com/auth/calendar.events" not in set(calendar_credential.get("scopes") or []):
            raise AssertionError("Calendar event-write scope was not issued")
        passed("oauth.calendar-write-scope-issued")

        run_id = os.environ.get("AETHERIS_PHASE8_RUN_ID", str(int(time.time()))).strip() or str(int(time.time()))
        start = datetime.now(timezone.utc) + timedelta(minutes=10)
        end = start + timedelta(minutes=15)
        calendar_target = f"{calendar_id}|{start.isoformat()}|{end.isoformat()}"
        summary = f"Aetheris Phase 8 live test-account validation {run_id}"

        github_action = create_live_action(github["id"], "GITHUB_CREATE_ISSUE", f"phase8-github-{run_id}", github_repo, summary)
        gmail_action = create_live_action(gmail["id"], "GMAIL_SEND_EMAIL", f"phase8-gmail-{run_id}", gmail_target, summary)
        calendar_action = create_live_action(calendar["id"], "CALENDAR_CREATE_EVENT", f"phase8-calendar-{run_id}", calendar_target, summary)

        for action, check_id in (
            (github_action, "live.github-unapproved-blocked"),
            (gmail_action, "live.gmail-unapproved-blocked"),
            (calendar_action, "live.calendar-unapproved-blocked"),
        ):
            status, _ = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{action['id']}/execute")
            if status != 409:
                raise AssertionError("an unapproved live action reached provider execution")
            passed(check_id)

        for action in (github_action, gmail_action, calendar_action):
            approve(action)

        github_result = execute(github_action, "github")
        if not str(github_result.get("externalReference", "")).startswith(("https://github.com/", "github:issue:")):
            raise Phase8ValidationError(
                "github.external-reference",
                "GitHub provider did not return an issue receipt",
                orchestrator_http_status=200,
            )
        passed("live.github-approved-executes")

        gmail_result = execute(gmail_action, "gmail")
        if not str(gmail_result.get("externalReference", "")).startswith("gmail:"):
            raise Phase8ValidationError(
                "gmail.external-reference",
                "Gmail provider did not return a message receipt",
                orchestrator_http_status=200,
            )
        passed("live.gmail-approved-executes")

        calendar_result = execute(calendar_action, "calendar")
        if not str(calendar_result.get("externalReference", "")).startswith("calendar:"):
            raise Phase8ValidationError(
                "calendar.external-reference",
                "Calendar provider did not return an event receipt",
                orchestrator_http_status=200,
            )
        passed("live.calendar-approved-executes")

        status, replay = request(BASE, "POST", f"/api/orchestrator/connectors/actions/{gmail_action['id']}/execute")
        if status != 200 or not isinstance(replay, dict) or replay.get("replay") is not True:
            raise AssertionError("local live-execution replay protection did not activate")
        if replay.get("externalReference") != gmail_result.get("externalReference"):
            raise AssertionError("replay changed the persisted provider receipt")
        passed("live.execution-replay-idempotent")

        status, actions = request(BASE, "GET", "/api/orchestrator/connectors/actions")
        if status != 200 or not isinstance(actions, list):
            raise AssertionError("connector action history is unavailable")
        proof_ids = {github_action["id"], gmail_action["id"], calendar_action["id"]}
        persisted = [item for item in actions if item.get("id") in proof_ids]
        if len(persisted) != 3 or any(
            item.get("status") != "EXECUTED" or item.get("executionMode") != "LIVE" or not item.get("externalReference")
            for item in persisted
        ):
            raise AssertionError("live provider receipts were not persisted")
        passed("live.receipts-persisted")

        for item in persisted:
            status, approval_rows = request(BASE, "GET", f"/api/orchestrator/approvals/task/{item['taskId']}")
            if status != 200 or not isinstance(approval_rows, list) or not any(
                row.get("status") == "APPROVED" and str(row.get("id")) == str(item.get("approvalId"))
                for row in approval_rows
            ):
                raise AssertionError("a live action lost its owner-approval audit link")
        passed("live.approval-audit-preserved")

        evidence_preview = json.dumps(report_payload(checks, "PASS"), sort_keys=True)
        sensitive_values = [gmail_target, calendar_id, github_repo]
        if any(value and value in evidence_preview for value in sensitive_values):
            raise AssertionError("sanitized evidence unexpectedly contains a target value")
        passed("live.evidence-sanitized")

        if contract.get("truth_boundary") != TRUTH_BOUNDARY:
            raise AssertionError("Phase 8 truth boundary changed during execution")
        passed("live.truth-boundary")

        missing = sorted(set(CHECK_IDS) - set(checks))
        extra = sorted(set(checks) - set(CHECK_IDS))
        if missing or extra:
            raise AssertionError("Phase 8 contract/check mismatch")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "capability": CAPABILITY, "checks": len(checks)}))
        return 0
    except Phase8ValidationError as error:
        write_report(
            output,
            checks,
            "FAIL",
            type(error).__name__,
            failure_stage=error.stage,
            orchestrator_http_status=error.orchestrator_http_status,
            provider_http_status=error.provider_http_status,
        )
        safe_summary = {
            "status": "FAIL",
            "capability": CAPABILITY,
            "error_type": type(error).__name__,
            "failure_stage": error.stage,
        }
        if error.orchestrator_http_status is not None:
            safe_summary["orchestrator_http_status"] = error.orchestrator_http_status
        if error.provider_http_status is not None:
            safe_summary["provider_http_status"] = error.provider_http_status
        print(json.dumps(safe_summary, sort_keys=True))
        return 1
    except Exception as error:  # noqa: BLE001
        write_report(output, checks, "FAIL", type(error).__name__, failure_stage="validator.unclassified")
        print(json.dumps({
            "status": "FAIL",
            "capability": CAPABILITY,
            "error_type": type(error).__name__,
            "failure_stage": "validator.unclassified",
        }, sort_keys=True))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
