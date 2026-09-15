#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME proof uses synthetic overnight signals against the real orchestrator service. "
    "It does not prove live email/DM/phone/billing/deployment connector execution, autonomous external sending, "
    "production scheduling, or physical-PC behavior."
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
        "capability": "proactive-executive-agent",
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
    parser.add_argument("--contract", default="build-evidence/runtime/proactive-executive-agent-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/proactive-executive-agent-report.json")
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

        next_briefing = (datetime.now(timezone.utc) + timedelta(hours=8)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        payload = {
            "ownerId": "runtime-proof-owner",
            "nextBriefingAt": next_briefing,
            "signals": [
                {"source": "inbox", "type": "FAQ", "subject": "How do I start?", "detail": "Approved FAQ", "trustedLowRisk": True, "signups": 0, "verificationStatus": "", "verificationRuns": 0},
                {"source": "community", "type": "DM", "subject": "Docs link please", "detail": "Approved common answer", "trustedLowRisk": True, "signups": 0, "verificationStatus": "", "verificationRuns": 0},
                {"source": "growth", "type": "PRODUCT_INTEREST", "subject": "Send the approved Aetheris link", "detail": "Approved product information", "trustedLowRisk": True, "signups": 0, "verificationStatus": "", "verificationRuns": 0},
                {"source": "inbox", "type": "EMAIL", "subject": "Partnership proposal", "detail": "Needs owner wording", "trustedLowRisk": False, "signups": 0, "verificationStatus": "", "verificationRuns": 0},
                {"source": "analytics", "type": "SIGNUP", "subject": "Overnight conversions", "detail": "Synthetic proof conversions", "trustedLowRisk": True, "signups": 4, "verificationStatus": "", "verificationRuns": 0},
                {"source": "github", "type": "CODE_UPDATE", "subject": "Regression fix", "detail": "Fix passed repeated checks", "trustedLowRisk": True, "signups": 0, "verificationStatus": "PASS", "verificationRuns": 3},
                {"source": "billing", "type": "BILLING", "subject": "Refund request", "detail": "Consequential financial action", "trustedLowRisk": False, "signups": 0, "verificationStatus": "", "verificationRuns": 0},
                {"source": "release", "type": "DEPLOYMENT", "subject": "Promote release", "detail": "Production-impacting action", "trustedLowRisk": False, "signups": 0, "verificationStatus": "", "verificationRuns": 0}
            ]
        }
        status, briefing = http_json("POST", "/api/orchestrator/executive/overnight-runs", payload)
        if status != 200 or not isinstance(briefing, dict) or not briefing.get("runId"):
            raise AssertionError(f"overnight run failed: status={status} body={briefing!r}")
        passed("executive.run-created")

        if briefing.get("autoHandled") != 3:
            raise AssertionError(f"expected 3 trusted low-risk auto-handled signals: {briefing!r}")
        passed("executive.low-risk-auto-handled")

        actions = briefing.get("actions")
        if not isinstance(actions, list) or len(actions) != len(payload["signals"]):
            raise AssertionError(f"actions did not map 1:1 to signals: {actions!r}")
        email_actions = [a for a in actions if a.get("type") == "EMAIL"]
        if len(email_actions) != 1 or email_actions[0].get("disposition") != "DRAFTED" or "no external send" not in email_actions[0].get("summary", ""):
            raise AssertionError(f"untrusted email was not safely drafted: {email_actions!r}")
        passed("executive.untrusted-external-drafted")

        billing = [a for a in actions if a.get("type") == "BILLING"]
        if len(billing) != 1 or billing[0].get("disposition") != "APPROVAL_REQUIRED" or not billing[0].get("approvalId"):
            raise AssertionError(f"billing action bypassed approval: {billing!r}")
        passed("executive.billing-approval-gated")

        deployment = [a for a in actions if a.get("type") == "DEPLOYMENT"]
        if len(deployment) != 1 or deployment[0].get("disposition") != "APPROVAL_REQUIRED" or not deployment[0].get("approvalId"):
            raise AssertionError(f"deployment action bypassed approval: {deployment!r}")
        passed("executive.deployment-approval-gated")

        if briefing.get("signups") != 4:
            raise AssertionError(f"signup evidence mismatch: {briefing!r}")
        passed("executive.signups-tracked")

        code = [a for a in actions if a.get("type") == "CODE_UPDATE"]
        if briefing.get("verifiedCodeUpdates") != 1 or len(code) != 1 or code[0].get("disposition") != "VERIFIED" or "3 successful checks" not in code[0].get("summary", ""):
            raise AssertionError(f"code verification evidence mismatch: {code!r}")
        passed("executive.code-update-verified")

        voice_id = briefing.get("voiceSessionId")
        status, sessions = http_json("GET", "/api/orchestrator/voice/sessions")
        if status != 200 or not isinstance(sessions, list) or not any(str(s.get("id")) == str(voice_id) for s in sessions):
            raise AssertionError(f"voice briefing session not persisted: voice={voice_id!r} sessions={sessions!r}")
        passed("executive.voice-briefing-created")

        if briefing.get("nextBriefingAt") != next_briefing:
            raise AssertionError(f"next briefing not retained: expected={next_briefing} actual={briefing.get('nextBriefingAt')}")
        passed("executive.next-briefing-retained")

        if any(not a.get("disposition") or not a.get("summary") for a in actions):
            raise AssertionError(f"action explainability incomplete: {actions!r}")
        passed("executive.actions-explainable")

        status, pending = http_json("GET", "/api/orchestrator/approvals/pending")
        pending_types = {item.get("actionType") for item in pending} if isinstance(pending, list) else set()
        if status != 200 or not {"EXECUTIVE_BILLING", "EXECUTIVE_DEPLOYMENT"}.issubset(pending_types):
            raise AssertionError(f"executive approvals not visible in pending surface: {pending!r}")
        passed("executive.pending-approvals-visible")

        status, history = http_json("GET", "/api/orchestrator/executive/briefings")
        if status != 200 or not isinstance(history, list) or not any(str(item.get("id")) == str(briefing["runId"]) for item in history):
            raise AssertionError(f"briefing history missing run: {history!r}")
        passed("executive.briefing-persisted")

        passed("executive.truth-boundary")
        missing = sorted(set(required) - set(checks))
        extra = sorted(set(checks) - set(required))
        if missing or extra:
            raise AssertionError(f"contract mismatch missing={missing} extra={extra}")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks), "runId": briefing["runId"]}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
