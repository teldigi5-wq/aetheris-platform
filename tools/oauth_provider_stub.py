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


def payload(handler: BaseHTTPRequestHandler, status: int, body: dict) -> None:
    raw = json.dumps(body, separators=(",", ":")).encode()
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json")
    handler.send_header("Content-Length", str(len(raw)))
    handler.end_headers()
    handler.wfile.write(raw)


class Handler(BaseHTTPRequestHandler):
    server_version = "AetherisOAuthStub/1.0"

    def log_message(self, fmt: str, *args) -> None:
        return

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            payload(self, 200, {"status": "UP"})
            return
        if self.path == "/metrics":
            payload(self, 200, {"unsafeMutations": UNSAFE_MUTATIONS, "issuedTokens": len(TOKENS)})
            return
        if self.path in {"/github/user", "/google/userinfo"}:
            auth = self.headers.get("Authorization", "")
            token = auth.removeprefix("Bearer ").strip()
            if token not in TOKENS:
                payload(self, 401, {"error": "invalid_token"})
                return
            if self.path == "/github/user":
                payload(self, 200, {"id": 424242, "login": "phase3-proof", "name": "Phase 3 Proof User"})
            else:
                payload(self, 200, {"sub": "google-phase3-proof", "email": "phase3@example.test", "name": "Phase 3 Proof User"})
            return
        payload(self, 404, {"error": "not_found"})

    def do_POST(self) -> None:  # noqa: N802
        global COUNTER, UNSAFE_MUTATIONS
        if self.path not in {"/github/token", "/google/token"}:
            UNSAFE_MUTATIONS += 1
            payload(self, 405, {"error": "provider_mutation_forbidden"})
            return
        size = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(size).decode()
        form = urllib.parse.parse_qs(raw)
        grant = form.get("grant_type", [""])[0]
        client_id = form.get("client_id", [""])[0]
        client_secret = form.get("client_secret", [""])[0]
        expected_id = os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_ID") if self.path.startswith("/github") else os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_ID")
        expected_secret = os.environ.get("AETHERIS_OAUTH_GITHUB_CLIENT_SECRET") if self.path.startswith("/github") else os.environ.get("AETHERIS_OAUTH_GOOGLE_CLIENT_SECRET")
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
        token = f"phase3-access-{COUNTER}"
        refresh = f"phase3-refresh-{COUNTER}"
        TOKENS.add(token)
        scope = "read:user user:email" if self.path.startswith("/github") else "openid email profile https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/calendar.readonly"
        payload(self, 200, {
            "access_token": token,
            "refresh_token": refresh,
            "token_type": "Bearer",
            "expires_in": 3600,
            "scope": scope,
        })


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
