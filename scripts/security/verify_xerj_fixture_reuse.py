#!/usr/bin/env python3
"""Pinned-tree reuse audit for the Gateway JWT test-only signing fixture.

Never prints, persists, uploads or includes the signing literal in a commit.
The test fixture's presence is not proof of production use or disuse.
The scan covers ONLY Git blobs at the exact proposed Aetheris source pin.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
ORIGIN = "gateway/src/test/java/io/aetheris/gateway/JwtAuthenticationFilterTest.java"
DECL = re.compile(
    rb'\bprivate\s+static\s+final\s+String\s+SECRET\s*=\s*"([^"\\\r\n]+)"\s*;'
)


def git(*args: str) -> bytes:
    return subprocess.check_output(
        ["git", "-C", str(ROOT), *args], stderr=subprocess.DEVNULL
    )


def require(ok: bool, reason: str) -> None:
    if not ok:
        raise ValueError(reason)


def paths_and_hashes(commit: str) -> list[tuple[str, str]]:
    items = []
    for entry in git("ls-tree", "-r", "-l", "-z", commit).split(b"\0"):
        if not entry:
            continue
        metadata, raw_path = entry.split(b"\t", 1)
        mode, kind, oid, size = metadata.split()
        path = raw_path.decode("utf-8")
        if kind != b"blob":
            continue
        # Never silently ignore a blob that could contain the same value.
        # An intentionally huge blob is a review blocker, not a pass.
        require(int(size) <= 10 * 1024 * 1024,
                "Over-limit blob requires separate fixture-reuse inspection")
        items.append((path, oid.decode("ascii")))
    require(items, "Pinned Git tree is empty")
    return items


def main() -> None:
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    pin = plan["source"]["sha"]
    selected = plan["paths"]
    require(plan["publication"]["hub_submission_authorized"] is False,
            "This check must never confer publication permission")
    require(ORIGIN not in selected, "Invalid selected corpus path")
    blobs = paths_and_hashes(pin)
    by_path = dict(blobs)
    require(ORIGIN in by_path, "Expected historical JWT test fixture missing")
    fixture_source = git("cat-file", "blob", by_path[ORIGIN])
    matches = DECL.findall(fixture_source)
    require(len(matches) == 1, "Expected exactly one JWT signing test fixture declaration")
    fixture = matches[0]
    require(len(fixture) == 32, "Known synthetic test fixture unexpectedly changed")

    # Check explicit negative and positive proofs without disclosing the value.
    assert fixture in fixture_source
    assert (fixture in b"prefix" + fixture + b"suffix")
    assert fixture not in b"synthetic unrelated source"

    duplicate_paths = []
    selected_duplicates = []
    scan_count = 0
    for path, oid in blobs:
        blob = git("cat-file", "blob", oid)
        if fixture in blob and path != ORIGIN:
            duplicate_paths.append(path)
            if any(path.startswith(folder + "/") for folder in selected):
                selected_duplicates.append(path)
        scan_count += 1

    if duplicate_paths:
        # Public path names only. Never print the fixture or matching lines.
        for path in duplicate_paths[:30]:
            print(f"XERJ_JWT_FIXTURE_REUSE_PATH: {path}", file=sys.stderr)
        raise ValueError("Pinned commit contains JWT fixture outside the original test")

    print(
        "XERJ_PINNED_JWT_FIXTURE_REUSE_CHECK_PASS "
        f"git_blobs={scan_count} other_matches=0 "
        "history_checked=NO runtime_reuse_proven_absent=NO "
        "publication=NOT_AUTHORIZED"
    )


if __name__ == "__main__":
    try:
        main()
    except (OSError, UnicodeError, ValueError, KeyError,
            subprocess.CalledProcessError) as exc:
        print(
            f"XERJ_JWT_FIXTURE_REUSE_CHECK_BLOCKED: {type(exc).__name__}: {exc}",
            file=sys.stderr
        )
        raise SystemExit(1)
