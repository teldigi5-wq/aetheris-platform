#!/usr/bin/env python3
"""Synthetic GET-only provider for Phase 5 validator CI."""
from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

MUTATIONS = 0


class Handler(BaseHTTPRequestHandler):
    server_version = "AetherisPhase5Stub/1.0"

    def log_message(self, fmt: str, *args) -> None:
        return

    def _json(self, status: int, payload: object) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            return self._json(200, {"status": "ok"})
        if path == "/metrics":
            return self._json(200, {"mutation_count": MUTATIONS})
        if path == "/user":
            return self._json(200, {"id": 123, "login": "phase5-proof"})
        if path == "/user/repos":
            return self._json(200, [{"id": 1}, {"id": 2}])
        if path == "/gmail/v1/users/me/messages":
            return self._json(200, {"messages": [{"id": "m1"}, {"id": "m2"}]})
        if path == "/calendar/v3/calendars/primary/events":
            return self._json(200, {"items": [{"id": "e1"}, {"id": "e2"}]})
        return self._json(404, {"error": "not found"})

    def _mutation(self) -> None:
        global MUTATIONS
        MUTATIONS += 1
        self._json(405, {"error": "mutations disabled"})

    do_POST = _mutation
    do_PUT = _mutation
    do_PATCH = _mutation
    do_DELETE = _mutation


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 19110), Handler).serve_forever()
