#!/usr/bin/env python3
"""B62-G7 CI ONLY: loopback mock authority for shared filter-contract tests.

NO customer JWT, signing key, persistent database or production network path.
This process models four approved test states, not real PostgreSQL revocation.
"""
import argparse
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

PINNED_PORT = 15377
CUTOFF = int(datetime(2026, 10, 27, 9, 0, tzinfo=timezone.utc).timestamp())
KID = "b62g7-synthetic-current"
ISSUER = "aetheris-identity"

class Handler(BaseHTTPRequestHandler):
    stage = "baseline"

    def do_GET(self):
        path = urlsplit(self.path)
        if path.path == "/health" and not path.query:
            self.send_reply(200, b"B62G7_TEST_ONLY_READY")
            return
        if path.path != "/decision" or len(self.path) > 350:
            self.send_reply(404, b"DENY")
            return
        try:
            args = parse_qs(path.query, strict_parsing=True, keep_blank_values=True)
            if set(args) != {"uid", "issuer", "kid", "iat"}:
                raise ValueError("unexpected query keys")
            if any(len(v) != 1 for v in args.values()):
                raise ValueError("duplicate security parameter")
            uid, issuer, kid, iat = (
                args[k][0] for k in ("uid", "issuer", "kid", "iat")
            )
            if (not uid.isascii() or not uid.isdecimal() or uid not in ("42", "43")
                    or issuer != ISSUER or kid != KID or not iat.isascii()
                    or not iat.isdecimal() or int(iat) <= 0):
                raise ValueError("unsigned or unexpected fixture claim")
            allow = self.stage == "baseline"
            if self.stage == "epoch":
                allow = uid != "42" or int(iat) > CUTOFF
            if self.stage == "blocked":
                allow = False
        except (ValueError, KeyError, OverflowError):
            allow = False
        self.send_reply(200, b"ALLOW" if allow else b"DENY")

    def send_reply(self, status, body):
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=US-ASCII")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Neither bearer tokens nor query parameters are logged.
        return

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--stage", choices=("baseline", "epoch", "blocked"), required=True)
    p.add_argument("--port", type=int, default=PINNED_PORT)
    args = p.parse_args()
    if args.port != PINNED_PORT:
        raise SystemExit("B62G7_PINNED_LOOPBACK_PORT_ONLY")
    Handler.stage = args.stage
    class FixtureHTTP(ThreadingHTTPServer):
        allow_reuse_address = True
    with FixtureHTTP(("127.0.0.1", PINNED_PORT), Handler) as server:
        print("B62G7_LOOPBACK_TEST_FIXTURE " + args.stage, flush=True)
        server.serve_forever(poll_interval=0.1)

if __name__ == "__main__":
    main()
