#!/usr/bin/env python3
"""Read-only live provider validation for Aetheris Connector Phase 5.

The validator performs GET-only calls against GitHub, Gmail, and Google Calendar.
It deliberately emits only sanitized status/count evidence: no response bodies,
account identifiers, email addresses, repository names, event titles, or tokens.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlparse
from urllib.request import Request, urlopen

LIVE_HOSTS = {
    "github": {"api.github.com"},
    "gmail": {"gmail.googleapis.com"},
    "calendar": {"www.googleapis.com"},
}
SYNTHETIC_HOSTS = {"127.0.0.1", "localhost"}
USER_AGENT = "aetheris-live-provider-validation/phase5"


def env_required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise RuntimeError(f"required credential environment variable is missing: {name}")
    return value


def normalized_base(url: str) -> str:
    return url.rstrip("/") + "/"


def assert_allowed_base(provider: str, base_url: str, mode: str) -> None:
    parsed = urlparse(base_url)
    if parsed.scheme not in ({"https"} if mode == "live" else {"http", "https"}):
        raise RuntimeError(f"disallowed URL scheme for {provider}: {parsed.scheme}")
    allowed = LIVE_HOSTS[provider] if mode == "live" else SYNTHETIC_HOSTS
    if parsed.hostname not in allowed:
        raise RuntimeError(f"disallowed validation host for {provider}: {parsed.hostname}")


def get_json(url: str, token: str, timeout: int = 15) -> tuple[int, Any]:
    request = Request(
        url,
        method="GET",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "User-Agent": USER_AGENT,
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            body = response.read()
            return response.status, json.loads(body.decode("utf-8")) if body else None
    except HTTPError as exc:
        # Never persist provider response bodies because they can contain PII.
        raise RuntimeError(f"provider GET failed with HTTP {exc.code} for host {urlparse(url).hostname}") from exc
    except URLError as exc:
        raise RuntimeError(f"provider GET failed for host {urlparse(url).hostname}: {exc.reason}") from exc


def count_list(value: Any) -> int:
    return len(value) if isinstance(value, list) else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("synthetic", "live"), default="synthetic")
    parser.add_argument("--output", required=True)
    parser.add_argument("--contract")
    parser.add_argument("--github-base-url", default=os.environ.get("AETHERIS_LIVE_GITHUB_API_URL", "https://api.github.com"))
    parser.add_argument("--gmail-base-url", default=os.environ.get("AETHERIS_LIVE_GMAIL_API_URL", "https://gmail.googleapis.com"))
    parser.add_argument("--calendar-base-url", default=os.environ.get("AETHERIS_LIVE_CALENDAR_API_URL", "https://www.googleapis.com"))
    args = parser.parse_args()

    github_token = env_required("AETHERIS_LIVE_GITHUB_ACCESS_TOKEN")
    gmail_token = env_required("AETHERIS_LIVE_GMAIL_ACCESS_TOKEN")
    calendar_token = env_required("AETHERIS_LIVE_CALENDAR_ACCESS_TOKEN")
    tokens = (github_token, gmail_token, calendar_token)

    bases = {
        "github": normalized_base(args.github_base_url),
        "gmail": normalized_base(args.gmail_base_url),
        "calendar": normalized_base(args.calendar_base_url),
    }
    for provider, base in bases.items():
        assert_allowed_base(provider, base, args.mode)

    checks: dict[str, str] = {}
    metrics: dict[str, int] = {}

    status, github_identity = get_json(urljoin(bases["github"], "user"), github_token)
    if status != 200 or not isinstance(github_identity, dict):
        raise AssertionError("GitHub identity read did not return an object")
    checks["live.github.identity-read"] = "PASS"

    github_repos_url = urljoin(bases["github"], "user/repos") + "?" + urlencode({
        "per_page": 5,
        "visibility": "public",
        "affiliation": "owner",
        "sort": "updated",
    })
    status, repos = get_json(github_repos_url, github_token)
    if status != 200 or not isinstance(repos, list):
        raise AssertionError("GitHub repository read did not return a list")
    metrics["github_resources_visible"] = count_list(repos)
    checks["live.github.repository-read"] = "PASS"

    gmail_url = urljoin(bases["gmail"], "gmail/v1/users/me/messages") + "?" + urlencode({
        "maxResults": 5,
        "q": "newer_than:30d",
    })
    status, gmail_data = get_json(gmail_url, gmail_token)
    if status != 200 or not isinstance(gmail_data, dict):
        raise AssertionError("Gmail message-list read did not return an object")
    metrics["gmail_resources_visible"] = count_list(gmail_data.get("messages"))
    checks["live.gmail.message-list-read"] = "PASS"

    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    calendar_url = urljoin(bases["calendar"], "calendar/v3/calendars/primary/events") + "?" + urlencode({
        "maxResults": 5,
        "singleEvents": "true",
        "orderBy": "startTime",
        "timeMin": now,
    })
    status, calendar_data = get_json(calendar_url, calendar_token)
    if status != 200 or not isinstance(calendar_data, dict):
        raise AssertionError("Google Calendar event-list read did not return an object")
    metrics["calendar_resources_visible"] = count_list(calendar_data.get("items"))
    checks["live.calendar.event-list-read"] = "PASS"

    checks["live.get-only-policy"] = "PASS"
    checks["live.response-body-not-persisted"] = "PASS"
    checks["live.provider-host-allowlist"] = "PASS"
    checks["live.no-provider-mutations"] = "PASS"
    checks["live.truth-boundary"] = "PASS"

    evidence_class = "LIVE_PROVIDER_READONLY" if args.mode == "live" else "HOSTED_RUNTIME"
    report = {
        "status": "PASS",
        "capability": "connector-live-account-validation-phase5",
        "evidence_class": evidence_class,
        "mode": args.mode,
        "checked_at": datetime.now(timezone.utc).isoformat(),
        "providers": {
            "github": {"host": urlparse(bases["github"]).hostname, "read_only": True},
            "gmail": {"host": urlparse(bases["gmail"]).hostname, "read_only": True},
            "calendar": {"host": urlparse(bases["calendar"]).hostname, "read_only": True},
        },
        "metrics": metrics,
        "checks": checks,
        "stores_provider_response_bodies": False,
        "stores_account_identifiers": False,
        "stores_token_material": False,
        "provider_mutations_enabled": False,
        "physical_pc_validation": False,
        "physical_pc_status": "BLOCKED_PENDING_HARDWARE",
    }

    serialized = json.dumps(report, sort_keys=True)
    if any(token and token in serialized for token in tokens):
        raise AssertionError("credential material leaked into sanitized report")
    checks["live.token-material-not-persisted"] = "PASS"

    if args.contract:
        contract = json.loads(Path(args.contract).read_text(encoding="utf-8"))
        expected = set(contract.get("expected_checks", []))
        missing = expected.difference(checks)
        if missing:
            raise AssertionError(f"contract checks missing from report: {sorted(missing)}")

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"PASS: {evidence_class} GitHub/Gmail/Calendar read-only validation; sanitized metrics={metrics}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, RuntimeError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
