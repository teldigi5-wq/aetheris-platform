#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = int(os.environ.get("AETHERIS_PHASE8_BROKER_PORT", "19091"))

GITHUB_TOKEN = os.environ.get("AETHERIS_PHASE8_GITHUB_ACCESS_TOKEN", "")
GMAIL_TOKEN = os.environ.get("AETHERIS_PHASE8_GMAIL_ACCESS_TOKEN", "")
CALENDAR_TOKEN = os.environ.get("AETHERIS_PHASE8_CALENDAR_ACCESS_TOKEN", "")

GITHUB_CLIENT_ID = os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_ID", "")
GITHUB_CLIENT_SECRET = os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_SECRET", "")
GOOGLE_CLIENT_ID = os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_ID", "")
GOOGLE_CLIENT_SECRET = os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET", "")


def send_json(handler: BaseHTTPRequestHandler, status: int, body: object) -> None:
    raw = json.dumps(body, separators=(",", ":")).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Cache-Control", "no-store")
    handler.send_header("Content-Length", str(len(raw)))
    handler.end_headers()
    handler.wfile.write(raw)


def bearer(handler: BaseHTTPRequestHandler) -> str:
    return handler.headers.get("Authorization", "").removeprefix("Bearer ").strip()


def known_token(value: str) -> bool:
    return bool(value) and value in {GITHUB_TOKEN, GMAIL_TOKEN, CALENDAR_TOKEN}


def read_form(handler: BaseHTTPRequestHandler) -> dict[str, list[str]]:
    size = int(handler.headers.get("Content-Length", "0"))
    raw = handler.rfile.read(size).decode("utf-8") if size else ""
    return urllib.parse.parse_qs(raw, keep_blank_values=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "AetherisPhase8CredentialBroker/1.0"

    def log_message(self, fmt: str, *args) -> None:
        # Deliberately suppress request logs so Authorization headers, codes,
        # and token-shaped material never appear in CI logs.
        return

    def do_GET(self) -> None:  # noqa: N802
        path = urllib.parse.urlparse(self.path).path
        if path == "/health":
            send_json(self, 200, {
                "status": "UP",
                "githubConfigured": bool(GITHUB_TOKEN),
                "gmailConfigured": bool(GMAIL_TOKEN),
                "calendarConfigured": bool(CALENDAR_TOKEN),
            })
            return

        if path == "/github/user":
            if bearer(self) != GITHUB_TOKEN or not GITHUB_TOKEN:
                send_json(self, 401, {"error": "invalid_token"})
                return
            send_json(self, 200, {
                "id": 88000001,
                "login": "aetheris-phase8-test-account",
                "name": "Aetheris Phase 8 Test Account",
            })
            return

        if path == "/google/userinfo":
            if not known_token(bearer(self)):
                send_json(self, 401, {"error": "invalid_token"})
                return
            send_json(self, 200, {
                "sub": "aetheris-phase8-test-account",
                "email": "phase8-test-account@example.invalid",
                "name": "Aetheris Phase 8 Test Account",
            })
            return

        send_json(self, 404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        path = urllib.parse.urlparse(self.path).path
        if path not in {"/github/token", "/google/token"}:
            send_json(self, 404, {"error": "not_found"})
            return

        form = read_form(self)
        grant = form.get("grant_type", [""])[0]
        code = form.get("code", [""])[0]
        verifier = form.get("code_verifier", [""])[0]
        client_id = form.get("client_id", [""])[0]
        client_secret = form.get("client_secret", [""])[0]

        if grant != "authorization_code" or not code or len(verifier) < 43:
            send_json(self, 400, {"error": "invalid_grant"})
            return

        if path == "/github/token":
            if client_id != GITHUB_CLIENT_ID or client_secret != GITHUB_CLIENT_SECRET or not GITHUB_TOKEN:
                send_json(self, 401, {"error": "invalid_client"})
                return
            token = GITHUB_TOKEN
            scope = "public_repo issues:write"
        else:
            if client_id != GOOGLE_CLIENT_ID or client_secret != GOOGLE_CLIENT_SECRET:
                send_json(self, 401, {"error": "invalid_client"})
                return
            lowered = code.lower()
            if "gmail" in lowered and GMAIL_TOKEN:
                token = GMAIL_TOKEN
                scope = "openid email profile https://www.googleapis.com/auth/gmail.send"
            elif "calendar" in lowered and CALENDAR_TOKEN:
                token = CALENDAR_TOKEN
                scope = "openid email profile https://www.googleapis.com/auth/calendar.events"
            else:
                send_json(self, 400, {"error": "phase8_token_not_configured"})
                return

        send_json(self, 200, {
            "access_token": token,
            "token_type": "Bearer",
            "expires_in": 1800,
            "scope": scope,
        })


def main() -> None:
    if not all([GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET]):
        raise SystemExit("Phase 8 broker requires ephemeral OAuth client credentials")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
