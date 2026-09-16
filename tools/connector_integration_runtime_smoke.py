#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME proof exercises the real connector registry, deduplicated inbound-event ingestion, "
    "normalization and executive-agent approval boundaries using synthetic provider deliveries. It does not "
    "prove live OAuth/provider authentication, cryptographic webhook verification, external message sending, "
    "calendar mutation, telephony, production provider connectivity, or physical-PC behavior."
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
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "connector-integration-layer-phase1",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
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
    parser.add_argument("--contract", default="build-evidence/runtime/connector-integration-phase1-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-integration-phase1-report.json")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required = [item["id"] for item in contract["checks"]]
    checks: dict[str, str] = {}
    output = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health()
        passed("service.orchestrator.health")

        gmail_registration = {
            "ownerId": "runtime-proof-owner",
            "provider": "GMAIL",
            "externalAccountRef": "runtime-proof-inbox",
            "displayName": "Runtime Proof Inbox",
            "capabilities": ["INGEST_MESSAGES", "DRAFT_REPLIES"],
        }
        status, gmail = http_json("POST", "/api/orchestrator/connectors/connections", gmail_registration)
        if status != 200 or not isinstance(gmail, dict) or not gmail.get("id"):
            raise AssertionError(f"gmail connector registration failed: status={status} body={gmail!r}")
        if gmail.get("status") != "REGISTERED":
            raise AssertionError(f"new connector should start REGISTERED: {gmail!r}")
        passed("connector.connection-registers")

        status, gmail_enabled = http_json(
            "PATCH", f"/api/orchestrator/connectors/connections/{gmail['id']}/status", {"status": "ENABLED"}
        )
        if status != 200 or gmail_enabled.get("status") != "ENABLED":
            raise AssertionError(f"gmail connector enable failed: status={status} body={gmail_enabled!r}")
        passed("connector.connection-enables")

        stripe_registration = {
            "ownerId": "runtime-proof-owner",
            "provider": "STRIPE",
            "externalAccountRef": "runtime-proof-billing",
            "displayName": "Runtime Proof Billing",
            "capabilities": ["INGEST_BILLING"],
        }
        status, stripe = http_json("POST", "/api/orchestrator/connectors/connections", stripe_registration)
        if status != 200 or not isinstance(stripe, dict) or not stripe.get("id"):
            raise AssertionError(f"stripe connector registration failed: status={status} body={stripe!r}")
        status, stripe_enabled = http_json(
            "PATCH", f"/api/orchestrator/connectors/connections/{stripe['id']}/status", {"status": "ENABLED"}
        )
        if status != 200 or stripe_enabled.get("status") != "ENABLED":
            raise AssertionError(f"stripe connector enable failed: status={status} body={stripe_enabled!r}")

        occurred_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        faq_event = {
            "connectionId": gmail["id"],
            "externalEventId": "gmail-msg-001",
            "type": "FAQ",
            "subject": "How do I start?",
            "detail": "Approved low-risk FAQ",
            "trustedLowRisk": True,
            "deliveryVerified": True,
            "signups": 0,
            "verificationStatus": "",
            "verificationRuns": 0,
            "occurredAt": occurred_at,
        }
        status, faq = http_json("POST", "/api/orchestrator/connectors/events", faq_event)
        if status != 200 or faq.get("duplicate") is not False or faq.get("disposition") != "AUTO_HANDLED":
            raise AssertionError(f"verified FAQ was not auto-handled: status={status} body={faq!r}")
        if faq.get("effectiveTrustedLowRisk") is not True:
            raise AssertionError(f"verified trusted event lost trust: {faq!r}")
        passed("connector.verified-low-risk-auto-handled")

        if not str(faq.get("source", "")).startswith("gmail:runtime-proof-inbox"):
            raise AssertionError(f"connector source was not normalized from registered provider/account: {faq!r}")
        passed("connector.source-normalized")

        email_event = {
            "connectionId": gmail["id"],
            "externalEventId": "gmail-msg-002",
            "type": "EMAIL",
            "subject": "Partnership proposal",
            "detail": "Requested trusted flag but delivery is not verified",
            "trustedLowRisk": True,
            "deliveryVerified": False,
            "signups": 0,
            "verificationStatus": "",
            "verificationRuns": 0,
            "occurredAt": occurred_at,
        }
        status, email = http_json("POST", "/api/orchestrator/connectors/events", email_event)
        if status != 200 or email.get("disposition") != "DRAFTED":
            raise AssertionError(f"unverified email did not downgrade to draft: status={status} body={email!r}")
        if email.get("effectiveTrustedLowRisk") is not False:
            raise AssertionError(f"unverified delivery incorrectly retained trusted-low-risk state: {email!r}")
        passed("connector.unverified-delivery-trust-downgraded")

        status, duplicate = http_json("POST", "/api/orchestrator/connectors/events", email_event)
        if status != 200 or duplicate.get("duplicate") is not True:
            raise AssertionError(f"duplicate provider event was not recognized: status={status} body={duplicate!r}")
        if duplicate.get("receiptId") != email.get("receiptId") or duplicate.get("executiveRunId") != email.get("executiveRunId"):
            raise AssertionError(f"duplicate event did not return original receipt/run: first={email!r} duplicate={duplicate!r}")
        passed("connector.duplicate-idempotent")

        billing_event = {
            "connectionId": stripe["id"],
            "externalEventId": "billing-event-001",
            "type": "BILLING",
            "subject": "Refund request",
            "detail": "Consequential financial action",
            "trustedLowRisk": False,
            "deliveryVerified": True,
            "signups": 0,
            "verificationStatus": "",
            "verificationRuns": 0,
            "occurredAt": occurred_at,
        }
        status, billing = http_json("POST", "/api/orchestrator/connectors/events", billing_event)
        if status != 200 or billing.get("disposition") != "APPROVAL_REQUIRED" or not billing.get("approvalId"):
            raise AssertionError(f"billing connector event bypassed owner approval: status={status} body={billing!r}")
        passed("connector.billing-approval-gated")

        status, pending = http_json("GET", "/api/orchestrator/approvals/pending")
        if status != 200 or not isinstance(pending, list) or not any(
            item.get("actionType") == "EXECUTIVE_BILLING" and str(item.get("id")) == str(billing.get("approvalId"))
            for item in pending
        ):
            raise AssertionError(f"connector-created billing approval not visible: {pending!r}")
        passed("connector.approval-visible")

        status, receipts = http_json("GET", "/api/orchestrator/connectors/receipts")
        if status != 200 or not isinstance(receipts, list):
            raise AssertionError(f"connector receipt history unavailable: status={status} body={receipts!r}")
        owned_receipts = [item for item in receipts if item.get("connectionId") in {gmail["id"], stripe["id"]}]
        if len(owned_receipts) != 3:
            raise AssertionError(f"expected exactly three persisted unique receipts, got {owned_receipts!r}")
        passed("connector.duplicate-no-second-receipt")
        passed("connector.receipts-persisted")

        status, connections = http_json("GET", "/api/orchestrator/connectors/connections")
        if status != 200 or not isinstance(connections, list):
            raise AssertionError(f"connector registry unavailable: status={status} body={connections!r}")
        serialized = json.dumps(connections, sort_keys=True).lower()
        forbidden = [needle for needle in ("password", "secret", "credential", "access_token", "refresh_token") if needle in serialized]
        if forbidden:
            raise AssertionError(f"connector registry exposed secret-like material: {forbidden!r}")
        passed("connector.no-secret-material")

        if email.get("disposition") != "DRAFTED" or "no external send" not in email.get("summary", "").lower():
            raise AssertionError(f"phase-1 untrusted external message did not preserve no-send boundary: {email!r}")
        if any("SEND_MESSAGES" in (item.get("capabilities") or []) for item in connections):
            raise AssertionError(f"phase-1 unexpectedly exposed outbound send capability: {connections!r}")
        passed("connector.no-outbound-send")

        status, briefings = http_json("GET", "/api/orchestrator/executive/briefings")
        run_ids = {str(item.get("id")) for item in briefings} if isinstance(briefings, list) else set()
        expected_runs = {str(faq.get("executiveRunId")), str(email.get("executiveRunId")), str(billing.get("executiveRunId"))}
        if status != 200 or not expected_runs.issubset(run_ids):
            raise AssertionError(f"connector events were not routed into persisted executive runs: {briefings!r}")
        passed("connector.executive-routing-persisted")

        passed("connector.truth-boundary")
        missing = sorted(set(required) - set(checks))
        extra = sorted(set(checks) - set(required))
        if missing or extra:
            raise AssertionError(f"contract mismatch missing={missing} extra={extra}")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks), "receipts": 3}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
