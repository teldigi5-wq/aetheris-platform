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
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME_SYNTHETIC_WRITE proves owner-approval orchestration, persisted idempotent action receipts "
    "and synthetic Gmail/Calendar/GitHub write adapters in hosted runtime. Live provider mutation remains "
    "fail-closed and is not proven; this is not production autonomy or physical-PC validation."
)


def http_json(method: str, path: str, payload: Any | None = None, timeout: int = 15) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
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


def wait_health(timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last: Any = None
    while time.monotonic() < deadline:
        try:
            status, body = http_json("GET", "/actuator/health", timeout=5)
            last = (status, body)
            if status == 200 and isinstance(body, dict) and body.get("status") == "UP":
                return
        except Exception as exc:  # noqa: BLE001
            last = repr(exc)
        time.sleep(2)
    raise AssertionError(f"orchestrator not healthy: {last!r}")


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    payload: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME_SYNTHETIC_WRITE",
        "capability": "connector-approved-actions-phase6",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "live_provider_mutation_proven": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        payload["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--contract", default="build-evidence/runtime/connector-approved-actions-phase6-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-approved-actions-phase6-report.json")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}
    output = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared check: {check_id}")
        checks[check_id] = "PASS"

    def register(provider: str, account: str, capability: str) -> dict[str, Any]:
        status, connection = http_json("POST", "/api/orchestrator/connectors/connections", {
            "ownerId": "phase6-runtime-owner",
            "provider": provider,
            "externalAccountRef": account,
            "displayName": f"Phase 6 {provider}",
            "capabilities": [capability],
        })
        if status != 200 or not isinstance(connection, dict) or not connection.get("id"):
            raise AssertionError(f"{provider} registration failed: status={status} body={connection!r}")
        status, enabled = http_json(
            "PATCH", f"/api/orchestrator/connectors/connections/{connection['id']}/status", {"status": "ENABLED"}
        )
        if status != 200 or enabled.get("status") != "ENABLED":
            raise AssertionError(f"{provider} enable failed: status={status} body={enabled!r}")
        return enabled

    def create_action(connection_id: str, kind: str, key: str, target: str, summary: str,
                      mode: str = "SYNTHETIC") -> dict[str, Any]:
        status, action = http_json("POST", "/api/orchestrator/connectors/actions", {
            "connectionId": connection_id,
            "actionKind": kind,
            "idempotencyKey": key,
            "targetRef": target,
            "summary": summary,
            "executionMode": mode,
        })
        if status != 200 or not isinstance(action, dict) or not action.get("id"):
            raise AssertionError(f"action create failed kind={kind}: status={status} body={action!r}")
        return action

    def approve(action: dict[str, Any]) -> None:
        status, decision = http_json(
            "POST", f"/api/orchestrator/approvals/{action['approvalId']}/decision",
            {"approved": True, "note": "Phase 6 hosted synthetic write proof"},
        )
        if status != 200 or decision.get("status") != "APPROVED":
            raise AssertionError(f"approval failed: status={status} body={decision!r}")

    def execute(action: dict[str, Any], provider: str) -> dict[str, Any]:
        status, result = http_json("POST", f"/api/orchestrator/connectors/actions/{action['id']}/execute")
        if status != 200 or result.get("status") != "EXECUTED" or result.get("replay") is not False:
            raise AssertionError(f"{provider} synthetic execution failed: status={status} body={result!r}")
        expected_prefix = f"synthetic://{provider.lower()}/"
        if not str(result.get("externalReference", "")).startswith(expected_prefix) or not result.get("executedAt"):
            raise AssertionError(f"{provider} synthetic receipt missing: {result!r}")
        return result

    try:
        wait_health()
        passed("service.orchestrator.health")

        status, policy = http_json("GET", "/api/orchestrator/connectors/actions/policy")
        if status != 200 or policy.get("syntheticWritesEnabled") is not True or policy.get("liveWritesEnabled") is not False:
            raise AssertionError(f"unexpected write policy: status={status} body={policy!r}")
        if policy.get("ownerApprovalRequired") is not True or policy.get("evidenceClass") != "HOSTED_RUNTIME_SYNTHETIC_WRITE":
            raise AssertionError(f"write policy lost approval/truth boundary: {policy!r}")
        passed("connector.write-policy-live-disabled")

        gmail = register("GMAIL", "phase6-gmail", "DRAFT_REPLIES")
        calendar = register("CALENDAR", "phase6-calendar", "READ_CALENDAR")
        github = register("GITHUB", "phase6-github", "INGEST_CODE_UPDATES")

        gmail_action = create_action(
            gmail["id"], "GMAIL_SEND_EMAIL", "phase6-gmail-001", "candidate@example.test",
            "Send the approved interview follow-up draft")
        calendar_action = create_action(
            calendar["id"], "CALENDAR_CREATE_EVENT", "phase6-calendar-001", "primary-calendar",
            "Create an approved interview preparation event")
        github_action = create_action(
            github["id"], "GITHUB_CREATE_ISSUE", "phase6-github-001", "teldigi5-wq/aetheris-platform",
            "Create an approved synthetic tracking issue")

        for action, expected in (
            (gmail_action, "CONNECTOR_WRITE_GMAIL_SEND_EMAIL"),
            (calendar_action, "CONNECTOR_WRITE_CALENDAR_CREATE_EVENT"),
            (github_action, "CONNECTOR_WRITE_GITHUB_CREATE_ISSUE"),
        ):
            if action.get("status") != "PENDING_APPROVAL" or not action.get("taskId") or not action.get("approvalId"):
                raise AssertionError(f"action bypassed approval creation: {action!r}")
            if action.get("actionType") != expected:
                raise AssertionError(f"unexpected approval action type: {action!r}")
        passed("connector.gmail-write-approval-required")
        passed("connector.calendar-write-approval-required")
        passed("connector.github-write-approval-required")

        status, blocked = http_json("POST", f"/api/orchestrator/connectors/actions/{gmail_action['id']}/execute")
        if status != 409:
            raise AssertionError(f"unapproved write was not conflict-blocked: status={status} body={blocked!r}")
        passed("connector.unapproved-write-blocked")

        status, pending = http_json("GET", "/api/orchestrator/approvals/pending")
        expected_ids = {gmail_action["approvalId"], calendar_action["approvalId"], github_action["approvalId"]}
        visible_ids = {str(item.get("id")) for item in pending} if status == 200 and isinstance(pending, list) else set()
        if not {str(value) for value in expected_ids}.issubset(visible_ids):
            raise AssertionError(f"connector approvals not visible: status={status} body={pending!r}")
        passed("connector.approvals-visible")

        for action in (gmail_action, calendar_action, github_action):
            approve(action)

        gmail_executed = execute(gmail_action, "GMAIL")
        passed("connector.approved-gmail-synthetic-executes")
        calendar_executed = execute(calendar_action, "CALENDAR")
        passed("connector.approved-calendar-synthetic-executes")
        github_executed = execute(github_action, "GITHUB")
        passed("connector.approved-github-synthetic-executes")

        status, duplicate_request = http_json("POST", "/api/orchestrator/connectors/actions", {
            "connectionId": gmail["id"],
            "actionKind": "GMAIL_SEND_EMAIL",
            "idempotencyKey": "phase6-gmail-001",
            "targetRef": "changed@example.test",
            "summary": "This replay must not create another task or approval",
            "executionMode": "SYNTHETIC",
        })
        if status != 200 or duplicate_request.get("replay") is not True:
            raise AssertionError(f"idempotent request replay failed: status={status} body={duplicate_request!r}")
        if duplicate_request.get("id") != gmail_action.get("id") or duplicate_request.get("approvalId") != gmail_action.get("approvalId"):
            raise AssertionError(f"request replay created a new action/approval: {duplicate_request!r}")
        passed("connector.idempotent-request-replay")

        status, duplicate_execution = http_json("POST", f"/api/orchestrator/connectors/actions/{gmail_action['id']}/execute")
        if status != 200 or duplicate_execution.get("replay") is not True:
            raise AssertionError(f"execution replay failed: status={status} body={duplicate_execution!r}")
        if duplicate_execution.get("externalReference") != gmail_executed.get("externalReference"):
            raise AssertionError(f"execution replay changed receipt: {duplicate_execution!r}")
        passed("connector.idempotent-execution-replay")

        status, actions = http_json("GET", "/api/orchestrator/connectors/actions")
        if status != 200 or not isinstance(actions, list):
            raise AssertionError(f"action history unavailable: status={status} body={actions!r}")
        proof_ids = {gmail_action["id"], calendar_action["id"], github_action["id"]}
        persisted = [item for item in actions if item.get("id") in proof_ids]
        if len(persisted) != 3 or any(item.get("status") != "EXECUTED" or not item.get("executedAt") for item in persisted):
            raise AssertionError(f"synthetic action receipts not persisted: {persisted!r}")
        passed("connector.receipts-persisted")

        for item in (gmail_executed, calendar_executed, github_executed):
            if not all(item.get(field) for field in ("provider", "actionKind", "summary", "taskId", "approvalId", "actionType")):
                raise AssertionError(f"connector action is not explainable/auditable: {item!r}")
            status, task_approvals = http_json("GET", f"/api/orchestrator/approvals/task/{item['taskId']}")
            if status != 200 or not isinstance(task_approvals, list) or not any(
                row.get("status") == "APPROVED" and str(row.get("id")) == str(item.get("approvalId"))
                for row in task_approvals
            ):
                raise AssertionError(f"approved action lost audit link: {item!r} approvals={task_approvals!r}")
        passed("connector.audit-explainable")

        live_action = create_action(
            gmail["id"], "GMAIL_SEND_EMAIL", "phase6-gmail-live-001", "live@example.test",
            "Explicit LIVE request used only to prove fail-closed behavior", "LIVE")
        approve(live_action)
        status, live_blocked = http_json("POST", f"/api/orchestrator/connectors/actions/{live_action['id']}/execute")
        if status != 409:
            raise AssertionError(f"live write did not fail closed: status={status} body={live_blocked!r}")
        status, live_after = http_json("GET", f"/api/orchestrator/connectors/actions/{live_action['id']}")
        if status != 200 or live_after.get("status") == "EXECUTED" or live_after.get("externalReference") is not None:
            raise AssertionError(f"live blocked action shows mutation evidence: {live_after!r}")
        passed("connector.live-write-fails-closed")

        if policy.get("truthBoundary") != TRUTH_BOUNDARY:
            raise AssertionError(f"service truth boundary differs from proof harness: {policy!r}")
        passed("connector.truth-boundary")

        missing = sorted(set(required) - set(checks))
        extra = sorted(set(checks) - set(required))
        if missing or extra:
            raise AssertionError(f"contract mismatch missing={missing} extra={extra}")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks), "synthetic_executions": 3}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
