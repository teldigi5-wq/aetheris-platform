#!/usr/bin/env python3
"""B4Y: real restored Audit HTTP readiness and independent deny-by-default auth.

Runs only on isolated CI Docker bridge. Never collects tokens, credentials or data.
"""
import json
import time
import urllib.error
import urllib.request

BASE = "http://audit-restored:8083"


def health_is_up(raw):
    try:
        value = json.loads(raw)
    except (ValueError, UnicodeError, TypeError):
        return False
    return isinstance(value, dict) and value.get("status") == "UP"


def read(path):
    if path not in ("/actuator/health", "/api/events"):
        raise ValueError("unsupported endpoint")
    req = urllib.request.Request(BASE + path, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, response.read(8192)
    except urllib.error.HTTPError as error:
        return error.code, error.read(8192)


def main():
    deadline = time.monotonic() + 130
    while time.monotonic() < deadline:
        try:
            code, payload = read("/actuator/health")
            if code == 200 and health_is_up(payload):
                break
        except (OSError, TimeoutError):
            pass
        time.sleep(3)
    else:
        raise AssertionError("B4Y_RESTORED_REAL_AUDIT_HEALTH_NOT_UP")
    code, _ = read("/api/events")
    if code != 401:
        raise AssertionError("B4Y_RESTORED_AUTH_GUARD_EXPECTED_401")
    print("B4Y_RESTORED_REAL_AUDIT_HEALTH_UP_AND_UNAUTHENTICATED_EVENTS_DENIED")


if __name__ == "__main__":
    main()
