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
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME proof exercises the real Dockerized Aetheris orchestrator and PostgreSQL against a local "
    "synthetic provider over real HTTP. It proves read-only GitHub/Gmail/Calendar retrieval, normalization, "
    "receipt deduplication and Proactive Executive Agent ingestion. It does not prove live production provider "
    "accounts, provider push delivery, outbound email, calendar writes, GitHub mutations, production secret "
    "manager durability, provider SLA behavior, or physical-PC behavior."
)


def request(base: str, method: str, path: str, payload: Any | None = None) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
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
        "ownerId": "phase4-proof-owner",
        "provider": provider,
        "externalAccountRef": external_ref,
        "displayName": f"Phase 4 {provider}",
        "capabilities": capabilities,
    })
    if status != 200 or not isinstance(data, dict) or not data.get("id"):
        raise AssertionError(f"connector registration failed: {provider=} {status=} {data=!r}")
    return data


def authorize(connection: dict[str, Any], suffix: str) -> dict[str, Any]:
    connection_id = connection["id"]
    status, auth = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/connections/{connection_id}/oauth/authorizations",
        {"redirectUri": f"http://localhost:7777/oauth/{suffix}"},
    )
    if status != 200 or not isinstance(auth, dict) or not auth.get("sessionId") or not auth.get("state"):
        raise AssertionError(f"OAuth start failed: {status=} {auth=!r}")
    status, credential = request(
        BASE,
        "POST",
        f"/api/orchestrator/connectors/oauth/authorizations/{auth['sessionId']}/complete",
        {"code": f"phase4-{suffix}-code", "state": auth["state"]},
    )
    if status != 200 or not isinstance(credential, dict) or credential.get("status") != "ACTIVE":
        raise AssertionError(f"OAuth completion failed: {status=} {credential=!r}")
    return credential


def sync(connection_id: str) -> dict[str, Any]:
    status, data = request(BASE, "POST", f"/api/orchestrator/connectors/connections/{connection_id}/sync")
    if status != 200 or not isinstance(data, dict):
        raise AssertionError(f"provider sync failed: {status=} {data=!r}")
    return data


def assert_first_sync(result: dict[str, Any], provider: str) -> None:
    if result.get("provider") != provider:
        raise AssertionError(f"provider mismatch: {result!r}")
    if result.get("fetched") != 2 or result.get("ingested") != 2:
        raise AssertionError(f"first sync did not ingest two items: {result!r}")
    if result.get("duplicates") != 0 or result.get("failed") != 0:
        raise AssertionError(f"first sync was not clean: {result!r}")
    if len(result.get("results", [])) != 2:
        raise AssertionError(f"first sync results missing: {result!r}")


def assert_duplicate_sync(result: dict[str, Any], provider: str) -> None:
    if result.get("provider") != provider:
        raise AssertionError(f"provider mismatch: {result!r}")
    if result.get("fetched") != 2 or result.get("duplicates") != 2:
        raise AssertionError(f"second sync did not deduplicate two items: {result!r}")
    if result.get("ingested") != 0 or result.get("failed") != 0:
        raise AssertionError(f"duplicate sync unexpectedly ingested/failed items: {result!r}")


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    data: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "connector-provider-sync-phase4",
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
    parser.add_argument("--contract", default="build-evidence/runtime/connector-provider-sync-phase4-contract.json")
    parser.add_argument("--output", default="build-evidence/runtime/connector-provider-sync-phase4-report.json")
    args = parser.parse_args()

    contract = json.loads((ROOT / args.contract).read_text(encoding="utf-8"))
    required = set(contract["expected_checks"])
    checks: dict[str, str] = {}
    output = ROOT / args.output

    def passed(check_id: str) -> None:
        if check_id not in required:
            raise AssertionError(f"undeclared check: {check_id}")
        checks[check_id] = "PASS"

    try:
        wait_health()
        passed("service.orchestrator.health")

        github = register("GITHUB", "424242", ["INGEST_CODE_UPDATES"])
        authorize(github, "github")
        passed("sync.github.connection-ready")

        gmail = register("GMAIL", "google-phase3-proof", ["INGEST_MESSAGES"])
        authorize(gmail, "gmail")
        passed("sync.gmail.connection-ready")

        calendar = register("CALENDAR", "google-phase3-proof", ["READ_CALENDAR"])
        authorize(calendar, "calendar")
        passed("sync.calendar.connection-ready")

        github_first = sync(github["id"])
        assert_first_sync(github_first, "GITHUB")
        passed("sync.github.reads-provider-data")

        gmail_first = sync(gmail["id"])
        assert_first_sync(gmail_first, "GMAIL")
        passed("sync.gmail.reads-provider-data")

        calendar_first = sync(calendar["id"])
        assert_first_sync(calendar_first, "CALENDAR")
        passed("sync.calendar.reads-provider-data")

        all_first_results = (
            github_first["results"] + gmail_first["results"] + calendar_first["results"]
        )
        if len(all_first_results) != 6 or any(not item.get("executiveRunId") for item in all_first_results):
            raise AssertionError(f"Executive Agent run linkage missing: {all_first_results!r}")
        passed("sync.executive-agent-receives-provider-signals")

        if sum(result["ingested"] for result in (github_first, gmail_first, calendar_first)) != 6:
            raise AssertionError("first provider sync pass did not ingest all six provider objects")
        passed("sync.first-pass-ingests-all-items")

        status, receipts = request(BASE, "GET", "/api/orchestrator/connectors/receipts")
        if status != 200 or not isinstance(receipts, list):
            raise AssertionError(f"connector receipts unavailable: {status=} {receipts=!r}")
        provider_types = {(item.get("provider"), item.get("signalType")) for item in receipts}
        if ("GITHUB", "CODE_UPDATE") not in provider_types:
            raise AssertionError(f"GitHub normalization missing: {provider_types!r}")
        passed("sync.github.normalized-to-code-update")
        if ("GMAIL", "EMAIL") not in provider_types:
            raise AssertionError(f"Gmail normalization missing: {provider_types!r}")
        passed("sync.gmail.normalized-to-email")
        if ("CALENDAR", "CALENDAR_EVENT") not in provider_types:
            raise AssertionError(f"Calendar normalization missing: {provider_types!r}")
        passed("sync.calendar.normalized-to-calendar-event")

        phase4_receipts = [
            item for item in receipts
            if item.get("provider") in {"GITHUB", "GMAIL", "CALENDAR"}
        ]
        if len(phase4_receipts) != 6:
            raise AssertionError(f"expected six persisted provider receipts: {phase4_receipts!r}")
        passed("sync.receipts-persisted")

        github_second = sync(github["id"])
        gmail_second = sync(gmail["id"])
        calendar_second = sync(calendar["id"])
        assert_duplicate_sync(github_second, "GITHUB")
        assert_duplicate_sync(gmail_second, "GMAIL")
        assert_duplicate_sync(calendar_second, "CALENDAR")
        passed("sync.second-pass-deduplicates-all-items")

        status, metrics = request(STUB, "GET", "/metrics")
        if status != 200 or metrics.get("unsafeMutations") != 0:
            raise AssertionError(f"provider mutation boundary violated: {metrics!r}")
        passed("sync.no-provider-mutations")

        exposed = json.dumps(
            [github_first, gmail_first, calendar_first, receipts],
            sort_keys=True,
        )
        forbidden = [
            "phase4-access-",
            "phase4-refresh-",
            "accessTokenReference",
            "refreshTokenReference",
            "clientSecret",
            "vault:",
        ]
        if any(value in exposed for value in forbidden):
            raise AssertionError("provider credential material/reference was exposed by synchronization")
        passed("sync.no-token-material-exposed")

        slack = register("SLACK", "phase4-unsupported-proof", ["INGEST_MESSAGES"])
        status, unsupported = request(
            BASE,
            "POST",
            f"/api/orchestrator/connectors/connections/{slack['id']}/sync",
        )
        if status != 422:
            raise AssertionError(f"unsupported provider did not fail closed: {status=} {unsupported=!r}")
        passed("sync.unsupported-provider-fails-closed")

        passed("sync.truth-boundary")

        missing = required - set(checks)
        if missing:
            raise AssertionError(f"missing checks: {sorted(missing)}")

        write_report(output, checks, "PASS")
        print(f"PASS: {len(checks)} connector provider-sync checks")
        return 0
    except Exception as exc:  # noqa: BLE001
        write_report(output, checks, "FAIL", repr(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
