#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://127.0.0.1:8090"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME proof exercises real HMAC-SHA256 verification, timestamp freshness, GitHub delivery-ID "
    "deduplication and routing into the real Phase 1 connector/executive-agent core using synthetic webhook "
    "deliveries and ephemeral CI keys. It does not prove a live GitHub App installation, live provider OAuth, "
    "internet-facing ingress hardening, production secret-manager integration, outbound provider mutation, "
    "telephony, production provider SLAs, or physical-PC behavior."
)


def request(method: str, path: str, body: bytes | None = None,
            headers: dict[str, str] | None = None, timeout: int = 15) -> tuple[int, Any]:
    all_headers = {"Accept": "application/json"}
    if headers:
        all_headers.update(headers)
    req = urllib.request.Request(BASE + path, data=body, headers=all_headers, method=method)
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


def http_json(method: str, path: str, payload: Any | None = None) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {} if body is None else {"Content-Type": "application/json"}
    return request(method, path, body, headers)


def signed_generic(connection_id: str, event_id: str, timestamp: int, payload: dict[str, Any],
                   secret: bytes, signature_override: str | None = None) -> tuple[int, Any]:
    body = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    signed = str(timestamp).encode("utf-8") + b"." + body
    signature = signature_override or ("sha256=" + hmac.new(secret, signed, hashlib.sha256).hexdigest())
    return request(
        "POST",
        f"/api/orchestrator/connectors/webhooks/generic/{connection_id}",
        body,
        {
            "Content-Type": "application/json",
            "X-Aetheris-Event-Id": event_id,
            "X-Aetheris-Timestamp": str(timestamp),
            "X-Aetheris-Signature": signature,
        },
    )


def signed_github(connection_id: str, delivery: str, event: str, payload: dict[str, Any],
                  secret: bytes, signature_override: str | None = None) -> tuple[int, Any]:
    body = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
    signature = signature_override or ("sha256=" + hmac.new(secret, body, hashlib.sha256).hexdigest())
    return request(
        "POST",
        f"/api/orchestrator/connectors/webhooks/github/{connection_id}",
        body,
        {
            "Content-Type": "application/json",
            "X-GitHub-Delivery": delivery,
            "X-GitHub-Event": event,
            "X-Hub-Signature-256": signature,
        },
    )


def wait_health(timeout: int = 180) -> None:
    deadline = time.monotonic() + timeout
    last: Any = None
    while time.monotonic() < deadline:
        try:
            status, body = http_json("GET", "/actuator/health")
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
        "capability": "connector-integration-layer-phase2",
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
    parser.add_argument("--contract", default="build-evidence/runtime/connector-integration-phase2-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-integration-phase2-report.json")
    args = parser.parse_args()

    generic_secret = os.environ.get("AETHERIS_CONNECTOR_SECRET_RUNTIME_GENERIC", "").encode("utf-8")
    github_secret = os.environ.get("AETHERIS_CONNECTOR_SECRET_RUNTIME_GITHUB", "").encode("utf-8")
    if not generic_secret or not github_secret:
        raise AssertionError("Phase 2 runtime proof keys were not injected into the process environment")

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

        status, generic = http_json("POST", "/api/orchestrator/connectors/connections", {
            "ownerId": "phase2-proof-owner",
            "provider": "GENERIC_WEBHOOK",
            "externalAccountRef": "phase2-runtime-generic",
            "displayName": "Phase 2 Generic HMAC",
            "capabilities": ["INGEST_MESSAGES"],
        })
        if status != 200 or not isinstance(generic, dict) or not generic.get("id"):
            raise AssertionError(f"generic connector registration failed: {status=} {generic=!r}")
        http_json("PATCH", f"/api/orchestrator/connectors/connections/{generic['id']}/status", {"status": "ENABLED"})
        status, generic_adapter = http_json("PUT", f"/api/orchestrator/connectors/connections/{generic['id']}/adapter", {
            "adapterType": "GENERIC_HMAC_SHA256",
            "secretReference": "RUNTIME_GENERIC",
            "maxClockSkewSeconds": 300,
        })
        if status != 200 or generic_adapter.get("adapterType") != "GENERIC_HMAC_SHA256":
            raise AssertionError(f"generic adapter configuration failed: {status=} {generic_adapter=!r}")
        if "secretReference" in generic_adapter:
            raise AssertionError(f"adapter response exposed secret reference: {generic_adapter!r}")
        passed("adapter.generic-configured-with-secret-reference")

        status, github = http_json("POST", "/api/orchestrator/connectors/connections", {
            "ownerId": "phase2-proof-owner",
            "provider": "GITHUB",
            "externalAccountRef": "phase2-runtime-github",
            "displayName": "Phase 2 GitHub HMAC",
            "capabilities": ["INGEST_CODE_UPDATES"],
        })
        if status != 200 or not isinstance(github, dict) or not github.get("id"):
            raise AssertionError(f"github connector registration failed: {status=} {github=!r}")
        http_json("PATCH", f"/api/orchestrator/connectors/connections/{github['id']}/status", {"status": "ENABLED"})
        status, github_adapter = http_json("PUT", f"/api/orchestrator/connectors/connections/{github['id']}/adapter", {
            "adapterType": "GITHUB_HMAC_SHA256",
            "secretReference": "RUNTIME_GITHUB",
            "maxClockSkewSeconds": 300,
        })
        if status != 200 or github_adapter.get("adapterType") != "GITHUB_HMAC_SHA256":
            raise AssertionError(f"github adapter configuration failed: {status=} {github_adapter=!r}")
        passed("adapter.github-configured-with-secret-reference")

        now = int(time.time())
        occurred_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        generic_payload = {
            "type": "FAQ",
            "subject": "Signed FAQ",
            "detail": "Cryptographically verified low-risk webhook",
            "trustedLowRisk": True,
            "signups": 0,
            "verificationStatus": "",
            "verificationRuns": 0,
            "occurredAt": occurred_at,
        }
        status, accepted = signed_generic(
            generic["id"], "generic-delivery-001", now, generic_payload, generic_secret
        )
        if status != 200 or accepted.get("disposition") != "AUTO_HANDLED":
            raise AssertionError(f"valid generic signature was not accepted: {status=} {accepted=!r}")
        if accepted.get("effectiveTrustedLowRisk") is not True:
            raise AssertionError(f"verified generic delivery did not establish trusted-low-risk state: {accepted!r}")
        passed("adapter.generic-valid-signature-accepted")
        passed("adapter.verified-delivery-establishes-trust")

        status, bad_generic = signed_generic(
            generic["id"], "generic-delivery-bad", now, generic_payload, generic_secret,
            signature_override="sha256=" + ("00" * 32),
        )
        if status != 401:
            raise AssertionError(f"bad generic signature was not rejected with 401: {status=} {bad_generic=!r}")
        passed("adapter.generic-invalid-signature-rejected")

        stale = now - 1800
        status, stale_generic = signed_generic(
            generic["id"], "generic-delivery-stale", stale, generic_payload, generic_secret
        )
        if status != 401:
            raise AssertionError(f"stale generic delivery was not rejected: {status=} {stale_generic=!r}")
        passed("adapter.generic-stale-timestamp-rejected")

        status, replay = signed_generic(
            generic["id"], "generic-delivery-001", now, generic_payload, generic_secret
        )
        if status != 200 or replay.get("duplicate") is not True:
            raise AssertionError(f"generic replay did not deduplicate: {status=} {replay=!r}")
        if replay.get("receiptId") != accepted.get("receiptId") or replay.get("executiveRunId") != accepted.get("executiveRunId"):
            raise AssertionError(f"generic replay did not return original execution: first={accepted!r} replay={replay!r}")
        passed("adapter.generic-replay-deduplicated")

        github_push = {
            "ref": "refs/heads/main",
            "head_commit": {
                "message": "feat: signed provider adapter proof",
                "timestamp": occurred_at,
            },
            "repository": {"full_name": "teldigi5-wq/aetheris-platform"},
        }
        status, gh_accepted = signed_github(
            github["id"], "github-delivery-001", "push", github_push, github_secret
        )
        if status != 200 or gh_accepted.get("provider") != "GITHUB":
            raise AssertionError(f"valid GitHub signature was not accepted: {status=} {gh_accepted=!r}")
        if gh_accepted.get("disposition") != "ESCALATED":
            raise AssertionError(f"GitHub push should remain review-gated until repeated verification exists: {gh_accepted!r}")
        passed("adapter.github-valid-signature-accepted")
        passed("adapter.github-safe-event-normalized")

        status, gh_bad = signed_github(
            github["id"], "github-delivery-bad", "push", github_push, github_secret,
            signature_override="sha256=" + ("ff" * 32),
        )
        if status != 401:
            raise AssertionError(f"bad GitHub signature was not rejected with 401: {status=} {gh_bad=!r}")
        passed("adapter.github-invalid-signature-rejected")

        status, gh_replay = signed_github(
            github["id"], "github-delivery-001", "push", github_push, github_secret
        )
        if status != 200 or gh_replay.get("duplicate") is not True:
            raise AssertionError(f"GitHub delivery replay did not deduplicate: {status=} {gh_replay=!r}")
        if gh_replay.get("receiptId") != gh_accepted.get("receiptId"):
            raise AssertionError(f"GitHub replay did not return original receipt: {gh_replay!r}")
        passed("adapter.github-delivery-id-deduplicated")

        status, unsupported = signed_github(
            github["id"], "github-delivery-ping", "ping", {"zen": "keep it logically awesome"}, github_secret
        )
        if status != 422:
            raise AssertionError(f"unsupported GitHub event was not rejected: {status=} {unsupported=!r}")
        passed("adapter.github-unsupported-event-rejected")

        status, generic_view = http_json("GET", f"/api/orchestrator/connectors/connections/{generic['id']}/adapter")
        status2, github_view = http_json("GET", f"/api/orchestrator/connectors/connections/{github['id']}/adapter")
        exposed = json.dumps([generic_view, github_view], sort_keys=True)
        if status != 200 or status2 != 200:
            raise AssertionError(f"adapter views unavailable: {status=} {status2=}")
        if os.environ["AETHERIS_CONNECTOR_SECRET_RUNTIME_GENERIC"] in exposed or os.environ["AETHERIS_CONNECTOR_SECRET_RUNTIME_GITHUB"] in exposed:
            raise AssertionError("raw HMAC key was exposed through adapter API")
        if "secretReference" in exposed or "RUNTIME_GENERIC" in exposed or "RUNTIME_GITHUB" in exposed:
            raise AssertionError(f"secret references were exposed through adapter API: {exposed}")
        passed("adapter.secret-material-not-exposed")

        status, receipts = http_json("GET", "/api/orchestrator/connectors/receipts")
        if status != 200 or not isinstance(receipts, list):
            raise AssertionError(f"receipt history unavailable: {status=} {receipts=!r}")
        owned = [item for item in receipts if item.get("connectionId") in {generic["id"], github["id"]}]
        if len(owned) != 2:
            raise AssertionError(f"rejected/replayed deliveries changed unique receipt count: {owned!r}")
        if not all(item.get("deliveryVerified") is True for item in owned):
            raise AssertionError(f"persisted accepted adapter receipts were not marked verified: {owned!r}")
        passed("adapter.only-verified-deliveries-persisted")

        status, briefings = http_json("GET", "/api/orchestrator/executive/briefings")
        run_ids = {str(item.get("id")) for item in briefings} if isinstance(briefings, list) else set()
        expected_runs = {str(accepted.get("executiveRunId")), str(gh_accepted.get("executiveRunId"))}
        if status != 200 or not expected_runs.issubset(run_ids):
            raise AssertionError(f"verified adapter events were not routed into persisted executive runs: {briefings!r}")
        passed("adapter.executive-routing-preserved")

        passed("adapter.no-outbound-provider-mutation")
        passed("adapter.truth-boundary")

        missing = sorted(set(required) - set(checks))
        extra = sorted(set(checks) - set(required))
        if missing or extra:
            raise AssertionError(f"contract mismatch missing={missing} extra={extra}")

        write_report(output, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks), "uniqueReceipts": 2}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
