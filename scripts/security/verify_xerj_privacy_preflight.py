#!/usr/bin/env python3
"""Deterministic metadata/privacy preflight for a pinned, public XERJ candidate.

This is an additional *narrow* preflight, not a replacement for Gitleaks,
copyright ownership review, or an actual human privacy/licence attestation.
Reads only explicitly selected blobs at an immutable commit via Git.
"""
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys
from urllib.parse import urlsplit

ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
EMAIL = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I)
HOME = re.compile(r"(?:/home/[A-Za-z0-9._-]+/|/Users/[A-Za-z0-9._-]+/|C:\\Users\\)", re.I)
WEB_URL = re.compile(r"https?://[A-Za-z0-9._:~/?#\[\]@!$&'()*+,;=%-]+", re.I)
THIRD_PARTY = re.compile(
    r"\b(?:SPDX-License-Identifier|Copyright\s*(?:\(\s*[cC]\s*\))?|"
    r"All\s+rights\s+reserved|Licensed\s+under)\b", re.I
)
PRIVATE_KEY = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----")
# Names seen in the pinned public source. Adding an external hostname requires
# a new explicit review; these names are loopback or internal service labels.
ALLOW_HOSTS = frozenset({
    "localhost", "127.0.0.1", "identity-service", "user-service",
    "audit-service", "orchestrator-service",
})


def check_text(path: str, value: str) -> list[str]:
    issues = []
    if EMAIL.search(value):
        issues.append("email-address")
    if HOME.search(value):
        issues.append("private-home-path")
    if THIRD_PARTY.search(value):
        issues.append("unreviewed-copyright-or-licence-mark")
    if PRIVATE_KEY.search(value):
        issues.append("private-key-header")
    for match in WEB_URL.finditer(value):
        # The source URL is public, but do not echo a URL or credential into CI.
        host = urlsplit(match.group(0)).hostname
        if host not in ALLOW_HOSTS:
            issues.append("unreviewed-url-hostname")
            break
    return sorted(set(issues))


def git(*args: str) -> bytes:
    return subprocess.check_output(
        ["git", "-C", str(ROOT), *args], stderr=subprocess.DEVNULL
    )


def main() -> int:
    # Sanity tests are independent of the source corpus; they must not rely on
    # scanner exceptions or ignore directives.
    assert check_text("safe", "http://identity-service:8080/api") == []
    assert check_text("unsafe", "dev@not-public.invalid") == ["email-address"]
    assert "private-home-path" in check_text("unsafe", r"C:\Users\someone\secret")
    assert "unreviewed-url-hostname" in check_text("unsafe", "https://unknown.example.org/data")
    assert "unreviewed-copyright-or-licence-mark" in check_text("unsafe", "Copyright 2021 Other")
    assert "private-key-header" in check_text(
        "unsafe", "-----BEGIN PRIVATE KEY-----"
    )

    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    pin, paths = plan["source"]["sha"], plan["paths"]
    assert plan["publication"]["hub_submission_authorized"] is False
    assert plan["publication"]["source_snapshot_approved"] is False
    # Git tree selector and byte counts must first pass the independent
    # verify_xerj_source_scope.py check in this workflow.
    entries = [
        row.split(b"\t", 1)[1].decode("utf-8")
        for row in git("ls-tree", "-r", "-z", pin, "--", *paths).split(b"\0")
        if row
    ]
    assert len(entries) == plan["expected_tree"]["files"]
    flags: dict[str, list[str]] = {}
    for path in entries:
        if not any(path.startswith(prefix + "/") for prefix in paths):
            raise AssertionError("A file escaped the sparse corpus scope")
        try:
            content = git("show", f"{pin}:{path}").decode("utf-8")
        except UnicodeDecodeError:
            flags[path] = ["non-utf8-data"]
            continue
        indicators = check_text(path, content)
        if indicators:
            flags[path] = indicators

    if flags:
        print("XERJ_PRIVACY_PREFLIGHT_BLOCKED: manual review required", file=sys.stderr)
        for path, reasons in sorted(flags.items()):
            # Only source paths and reason codes, never matched values.
            print(f"{path}: {','.join(reasons)}", file=sys.stderr)
        return 1
    print(
        f"XERJ_PRIVACY_METADATA_PREFLIGHT_PASS pin={pin} files={len(entries)} "
        "human_licence_review=PENDING human_privacy_review=PENDING "
        "hub_publication=NOT_AUTHORIZED"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError, AssertionError) as exc:
        print(f"XERJ_PRIVACY_PREFLIGHT_ERROR: {type(exc).__name__}", file=sys.stderr)
        raise SystemExit(1)
