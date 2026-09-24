#!/usr/bin/env python3
"""Hosted browser-auth proof for the Aetheris dashboard boundary.

This proof exercises dashboard nginx -> gateway -> identity-service. It never writes
raw bearer credentials to its report and does not claim production or physical-PC
validation.
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DASHBOARD_ORIGIN = "http://127.0.0.1:3000"
COOKIE_SENTINEL = "COOKIE_BOUND"
COOKIE_NAME = "aetheris_refresh"
TRUTH_BOUNDARY = (
    "HOSTED_RUNTIME browser-auth evidence proves only the checked dashboard cookie "
    "and same-origin behavior in GitHub-hosted Docker Compose. It is not production "
    "deployment evidence, a penetration test, or physical-PC validation."
)


def request_json(
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 15,
) -> tuple[int, Any, dict[str, str]]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = {"Accept": "application/json", **(headers or {})}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        DASHBOARD_ORIGIN + path,
        data=body,
        headers=request_headers,
        method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            raw = response.read().decode("utf-8")
            response_headers = dict(response.headers.items())
    except urllib.error.HTTPError as error:
        status = error.code
        raw = error.read().decode("utf-8")
        response_headers = dict(error.headers.items())

    if not raw.strip():
        parsed: Any = None
    else:
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = raw
    return status, parsed, response_headers


def header(headers: dict[str, str], name: str) -> str | None:
    lowered = name.lower()
    for key, value in headers.items():
        if key.lower() == lowered:
            return value
    return None


def expect_status(actual: int, expected: int, context: str) -> None:
    if actual != expected:
        raise AssertionError(f"{context}: expected HTTP {expected}, got {actual}")


def extract_cookie(set_cookie: str | None) -> tuple[str, str]:
    if not set_cookie:
        raise AssertionError("browser auth response did not set the refresh cookie")
    pair = set_cookie.split(";", 1)[0]
    name, separator, value = pair.partition("=")
    if separator != "=" or name != COOKIE_NAME or not value:
        raise AssertionError("browser auth response set an unexpected refresh cookie shape")
    return pair, value


def assert_cookie_policy(set_cookie: str | None) -> tuple[str, str]:
    pair, value = extract_cookie(set_cookie)
    policy = set_cookie or ""
    if "HttpOnly" not in policy:
        raise AssertionError("browser refresh cookie is not HttpOnly")
    if "SameSite=Strict" not in policy:
        raise AssertionError("browser refresh cookie is not SameSite=Strict")
    if "Path=/api/auth" not in policy:
        raise AssertionError("browser refresh cookie is not scoped to /api/auth")
    return pair, value


def write_report(path: Path, checks: dict[str, str], status: str, error: str | None = None) -> None:
    report: dict[str, Any] = {
        "schema_version": 1,
        "evidence_class": "HOSTED_RUNTIME",
        "capability": "browser-auth",
        "environment": "github-hosted-ubuntu-docker-compose",
        "physical_pc_validation": False,
        "production_deployment_claim": False,
        "truth_boundary": TRUTH_BOUNDARY,
        "status": status,
        "checks": dict(sorted(checks.items())),
    }
    if error:
        report["error"] = error
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        default="build-evidence/runtime/browser-auth-runtime-report.json",
    )
    args = parser.parse_args()
    output_path = ROOT / args.output
    checks: dict[str, str] = {}

    def passed(check_id: str) -> None:
        checks[check_id] = "PASS"

    try:
        stamp = int(time.time())
        email = f"browser-proof-{stamp}@aetheris.local"
        registration = {
            "name": "Browser Proof",
            "email": email,
            "password": "browser-proof-input-123",
        }
        same_origin = {"Origin": DASHBOARD_ORIGIN}

        status, _, _ = request_json(
            "POST",
            "/api/auth/browser/register",
            registration,
            headers=same_origin,
        )
        expect_status(status, 404, "direct dashboard browser-auth implementation path")
        passed("browser.implementation-path-hidden")

        status, _, _ = request_json(
            "POST",
            "/api/auth/register",
            registration,
            headers={"Origin": "http://cross-origin.invalid"},
        )
        expect_status(status, 403, "cross-origin browser registration")
        passed("browser.cross-origin-register-rejected")

        status, registered, register_headers = request_json(
            "POST", "/api/auth/register", registration, headers=same_origin
        )
        expect_status(status, 201, "same-origin browser registration")
        if not isinstance(registered, dict):
            raise AssertionError("browser registration did not return JSON")
        if registered.get("refreshToken") != COOKIE_SENTINEL:
            raise AssertionError("browser registration exposed a refresh value other than the binding sentinel")
        if not isinstance(registered.get("accessToken"), str) or not registered["accessToken"]:
            raise AssertionError("browser registration did not return a short-lived access token")
        first_cookie_pair, first_cookie_value = assert_cookie_policy(
            header(register_headers, "Set-Cookie")
        )
        if first_cookie_value == COOKIE_SENTINEL:
            raise AssertionError("HttpOnly cookie used the public binding sentinel instead of an opaque credential")
        cache_control = header(register_headers, "Cache-Control") or ""
        if "no-store" not in cache_control.lower():
            raise AssertionError("browser auth response is missing Cache-Control: no-store")
        passed("browser.registration-cookie-bound")

        status, refreshed, refresh_headers = request_json(
            "POST",
            "/api/auth/refresh",
            {"refreshToken": COOKIE_SENTINEL},
            headers={**same_origin, "Cookie": first_cookie_pair},
        )
        expect_status(status, 200, "browser cookie refresh rotation")
        if not isinstance(refreshed, dict) or refreshed.get("refreshToken") != COOKIE_SENTINEL:
            raise AssertionError("browser refresh response exposed a non-sentinel refresh value")
        second_cookie_pair, second_cookie_value = assert_cookie_policy(
            header(refresh_headers, "Set-Cookie")
        )
        if second_cookie_value == first_cookie_value:
            raise AssertionError("browser refresh did not rotate the opaque HttpOnly credential")
        passed("browser.refresh-rotates-cookie")

        status, _, _ = request_json(
            "POST",
            "/api/auth/refresh",
            {"refreshToken": COOKIE_SENTINEL},
            headers={**same_origin, "Cookie": first_cookie_pair},
        )
        expect_status(status, 401, "replay of rotated browser refresh cookie")
        passed("browser.rotated-cookie-replay-rejected")

        status, _, _ = request_json(
            "POST",
            "/api/auth/refresh",
            {"refreshToken": COOKIE_SENTINEL},
            headers={"Origin": "http://cross-origin.invalid", "Cookie": second_cookie_pair},
        )
        expect_status(status, 403, "cross-origin browser refresh")
        passed("browser.cross-origin-refresh-rejected")

        status, _, logout_headers = request_json(
            "POST",
            "/api/auth/logout",
            {"refreshToken": COOKIE_SENTINEL},
            headers={**same_origin, "Cookie": second_cookie_pair},
        )
        expect_status(status, 204, "browser logout")
        clear_cookie = header(logout_headers, "Set-Cookie") or ""
        if COOKIE_NAME + "=" not in clear_cookie or "Max-Age=0" not in clear_cookie:
            raise AssertionError("browser logout did not expire the HttpOnly refresh cookie")
        passed("browser.logout-expires-cookie")

        status, _, _ = request_json(
            "POST",
            "/api/auth/refresh",
            {"refreshToken": COOKIE_SENTINEL},
            headers={**same_origin, "Cookie": second_cookie_pair},
        )
        expect_status(status, 401, "browser refresh after logout")
        passed("browser.logout-revokes-cookie")

        write_report(output_path, checks, "PASS")
        print(json.dumps({"status": "PASS", "checks": len(checks)}, sort_keys=True))
        return 0
    except Exception as exc:  # noqa: BLE001 - evidence boundary must record failure
        write_report(output_path, checks, "FAIL", str(exc))
        raise


if __name__ == "__main__":
    raise SystemExit(main())
