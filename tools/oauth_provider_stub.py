#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = int(os.environ.get("AETHERIS_OAUTH_STUB_PORT", "19090"))
TOKENS: set[str] = set()
COUNTER = 0
UNSAFE_MUTATIONS = 0


def payload(handler: BaseHTTPRequestHandler, status: int, body: object) -> None:
    raw = json.dumps(body, separators=(",", ":")).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(raw)))
    handler.end_headers()
    handler.wfile.write(raw)


def bearer(handler: BaseHTTPRequestHandler) -> str:
    return handler.headers.get("Authorization", "").removeprefix("Bearer ").strip()


def authorized(handler: BaseHTTPRequestHandler) -> bool:
    if bearer(handler) not in TOKENS:
        payload(handler, 401, {"error": "invalid_token"})
        return False
    return True


class Handler(BaseHTTPRequestHandler):
    server_version = "AetherisOAuthStub/1.1"

    def log_message(self, fmt: str, *args) -> None:
        return

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path == "/health":
            payload(self, 200, {"status": "UP"})
            return
        if path == "/metrics":
            payload(self, 200, {"unsafeMutations": UNSAFE_MUTATIONS, "issuedTokens": len(TOKENS)})
            return

        if path in {"/github/user", "/google/userinfo"}:
            if not authorized(self):
                return
            if path == "/github/user":
                payload(self, 200, {"id": 424242, "login": "phase3-proof", "name": "Phase 3 Proof User"})
            else:
                payload(self, 200, {
                    "sub": "google-phase3-proof",
                    "email": "phase3@example.test",
                    "name": "Phase 3 Proof User",
                })
            return

        if path == "/github/repos":
            if not authorized(self):
                return
            payload(self, 200, [
                {
                    "id": 1001,
                    "name": "aetheris-platform",
                    "full_name": "proof/aetheris-platform",
                    "description": "Synthetic read-only repository activity.",
                    "pushed_at": "2026-09-18T05:30:00Z",
                },
                {
                    "id": 1002,
                    "name": "portfolio",
                    "full_name": "proof/portfolio",
                    "description": "Synthetic read-only portfolio activity.",
                    "pushed_at": "2026-09-18T05:45:00Z",
                },
            ])
            return

        if path == "/gmail/messages":
            if not authorized(self):
                return
            payload(self, 200, {
                "messages": [
                    {"id": "gmail-proof-1", "threadId": "thread-1"},
                    {"id": "gmail-proof-2", "threadId": "thread-2"},
                ],
                "resultSizeEstimate": 2,
            })
            return

        if path.startswith("/gmail/messages/"):
            if not authorized(self):
                return
            message_id = path.rsplit("/", 1)[-1]
            messages = {
                "gmail-proof-1": {
                    "id": "gmail-proof-1",
                    "threadId": "thread-1",
                    "internalDate": "1789707600000",
                    "snippet": "Can you send the updated architecture overview?",
                    "payload": {
                        "headers": [
                            {"name": "Subject", "value": "Architecture overview"},
                            {"name": "From", "value": "Recruiter One <one@example.test>"},
                            {"name": "Date", "value": "Fri, 18 Sep 2026 05:00:00 +0000"},
                        ]
                    },
                },
                "gmail-proof-2": {
                    "id": "gmail-proof-2",
                    "threadId": "thread-2",
                    "internalDate": "1789709400000",
                    "snippet": "Reminder about the project discussion.",
                    "payload": {
                        "headers": [
                            {"name": "Subject", "value": "Project discussion"},
                            {"name": "From", "value": "Mentor Two <two@example.test>"},
                            {"name": "Date", "value": "Fri, 18 Sep 2026 05:30:00 +0000"},
                        ]
                    },
                },
            }
            if message_id not in messages:
                payload(self, 404, {"error": "message_not_found"})
                return
            payload(self, 200, messages[message_id])
            return

        if path == "/calendar/events":
            if not authorized(self):
                return
            payload(self, 200, {
                "items": [
                    {
                        "id": "calendar-proof-1",
                        "status": "confirmed",
                        "summary": "Aetheris design review",
                        "updated": "2026-09-18T05:00:00Z",
                        "start": {"dateTime": "2026-09-19T03:30:00Z"},
                        "end": {"dateTime": "2026-09-19T04:00:00Z"},
                    },
                    {
                        "id": "calendar-proof-2",
                        "status": "confirmed",
                        "summary": "Interview preparation",
                        "updated": "2026-09-18T05:10:00Z",
                        "start": {"dateTime": "2026-09-20T06:00:00Z"},
                        "end": {"dateTime": "2026-09-20T07:00:00Z"},
                    },
                ]
            })
            return

        payload(self, 404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        global COUNTER, UNSAFE_MUTATIONS
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path not in {"/github/token", "/google/token"}:
            UNSAFE_MUTATIONS += 1
            payload(self, 405, {"error": "provider_mutation_forbidden"})
            return

        size = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(size).decode()
        form = urllib.parse.parse_qs(raw)
        grant = form.get("grant_type", [""])[0]
        client_id = form.get("client_id", [""])[0]
        client_secret = form.get("client_secret", [""])[0]
        expected_id = (
            os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_ID")
            if path.startswith("/github")
            else os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_ID")
        )
        expected_secret = (
            os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_SECRET")
            if path.startswith("/github")
            else os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET")
        )
        if not client_id or client_id != expected_id or not client_secret or client_secret != expected_secret:
            payload(self, 401, {"error": "invalid_client"})
            return

        if grant == "authorization_code":
            code = form.get("code", [""])[0]
            verifier = form.get("code_verifier", [""])[0]
            if not code or len(verifier) < 43:
                payload(self, 400, {"error": "invalid_grant"})
                return
        elif grant == "refresh_token":
            if not form.get("refresh_token", [""])[0]:
                payload(self, 400, {"error": "invalid_grant"})
                return
        else:
            payload(self, 400, {"error": "unsupported_grant_type"})
            return

        COUNTER += 1
        token = f"phase4-access-{COUNTER}"
        refresh = f"phase4-refresh-{COUNTER}"
        TOKENS.add(token)
        scope = (
            "read:user user:email"
            if path.startswith("/github")
            else "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.readonly"
        )
        payload(self, 200, {
            "access_token": token,
            "refresh_token": refresh,
            "token_type": "Bearer",
            "expires_in": 3600,
            "scope": scope,
        })


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
