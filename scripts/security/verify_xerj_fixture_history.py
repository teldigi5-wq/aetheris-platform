#!/usr/bin/env python3
"""Scan every distinct reachable Git blob for a known synthetic JWT fixture.

The value itself is never logged, printed or written to a new file. This only
narrows exact-byte reuse; it does NOT prove past or live credential safety,
capture all unreachable history, or replace Gitleaks and human source review.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys
import threading

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
ORIGIN = "gateway/src/test/java/io/aetheris/gateway/JwtAuthenticationFilterTest.java"
DECL = re.compile(
    rb'\bprivate\s+static\s+final\s+String\s+SECRET\s*=\s*"([^"\\\r\n]+)"\s*;'
)

def git(*args: str) -> bytes:
    return subprocess.check_output(
        ["git", "-C", str(ROOT), *args],
        stderr=subprocess.DEVNULL, timeout=120
    )

def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)

def verify_reachable_tree_paths(extra: set[bytes]) -> None:
    """Resolve every matching blob through every reachable root tree, not
    the single representative object path from git rev-list --objects.
    Only exact public Git paths and counts are emitted. Still NOT a check
    of deployed credentials, deleted branches or external repositories.
    """
    require(bool(extra), "Missing unexpected historical blob IDs")
    roots = set(git("log", "--all", "--format=%T").splitlines())
    require(0 < len(roots) <= 10000,
            "Historical tree provenance budget exceeded; review manually")
    require(all(re.fullmatch(rb"[0-9a-f]{40}", t) for t in roots),
            "Unexpected historical tree identifier")
    found: dict[bytes, set[bytes]] = {oid: set() for oid in extra}
    for tree in sorted(roots):
        result = subprocess.run(
            ["git", "-C", str(ROOT), "ls-tree", "-r", "-z",
             tree.decode("ascii")],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            check=False, timeout=30
        )
        require(result.returncode == 0 and
                len(result.stdout) <= 32 * 1024 * 1024,
                "Historical Git tree traversal failed or exceeded budget")
        for entry in result.stdout.split(b"\0"):
            if not entry:
                continue
            header, sep, rel = entry.partition(b"\t")
            require(sep == b"\t" and
                    len(header.split()) == 3 and
                    len(rel) <= 512,
                    "Malformed historical Git tree entry")
            mode, kind, oid = header.split()
            if oid not in extra:
                continue
            require(kind == b"blob" and
                    bool(re.fullmatch(rb"[0-9a-f]{40}", oid)),
                    "Unexpected historical fixture object type")
            # No source lines, fixture bytes or unescaped terminal data.
            require(bool(re.fullmatch(rb"[A-Za-z0-9._/+~-]+", rel)),
                    "Historical matched path needs safe manual inspection")
            found[oid].add(rel)
    require(all(found.values()),
            "Matching history blob lacks reachable path provenance")
    paths = set().union(*found.values())
    in_tests = {rel for rel in paths if b"/src/test/" in rel}
    outside = paths - in_tests
    print(
        "XERJ_HISTORICAL_FIXTURE_TREE_PROVENANCE "
        f"reachable_root_trees={len(roots)} matched_extra_blobs={len(extra)} "
        f"distinct_paths={len(paths)} test_paths={len(in_tests)} "
        f"non_test_paths={len(outside)} "
        "credential_use_proven_absent=NO human_review=PENDING",
        file=sys.stderr
    )
    for rel in sorted(outside)[:15]:
        print(
            "XERJ_HISTORICAL_FIXTURE_NON_TEST_PATH: " +
            rel.decode("ascii"),
            file=sys.stderr
        )
    require(not outside,
            "Known fixture blob reachable outside test source paths")


def main() -> None:
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    pin = plan["source"]["sha"]
    require(plan["publication"]["hub_submission_authorized"] is False,
            "This scan cannot authorize XERJ publication")
    require(ORIGIN.startswith("gateway/src/test/"),
            "The original synthetic fixture must remain excluded")
    require(not any(ORIGIN.startswith(path + "/") for path in plan["paths"]),
            "The known JWT test fixture is accidentally selected for public indexing")
    source = git("show", pin + ":" + ORIGIN)
    matches = DECL.findall(source)
    require(len(matches) == 1 and len(matches[0]) == 32,
            "Expected exactly one pinned historical 32-byte JWT test fixture")
    fixture = matches[0]
    require(fixture in (b"test" + fixture + b"end"),
            "Known fixture search self-test failed")
    require(fixture not in b"synthetic example with no embedded token",
            "Non-matching bytes erroneously accepted")

    # Git object IDs are not secrets, but never print source contents or paths.
    rows = git("rev-list", "--objects", "--all").splitlines()
    oids = []
    seen = set()
    representative_paths = {}
    for line in rows:
        fields = line.split(b" ", 1)
        oid = fields[0]
        if len(fields) == 2:
            representative_paths[oid] = fields[1]
        require(bool(re.fullmatch(rb"[0-9a-f]{40}", oid)),
                "Unexpected historical Git object identifier")
        if oid not in seen:
            seen.add(oid)
            oids.append(oid)
    require(0 < len(oids) <= 500000, "Unexpected repository history size")

    commits = git("log", "--all", "--format=%H", "--", ORIGIN).splitlines()
    require(commits, "Historical origin file is not reachable")
    allowed = set()
    for commit in commits:
        require(bool(re.fullmatch(rb"[0-9a-f]{40}", commit)),
                "Unexpected origin commit ID")
        revision = subprocess.run(
            ["git", "-C", str(ROOT), "rev-parse", "--verify",
             commit.decode() + ":" + ORIGIN],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            check=False, timeout=15
        )
        # A deletion commit touches the path but has no blob at its HEAD.
        if revision.returncode != 0:
            continue
        oid = revision.stdout.strip()
        require(bool(re.fullmatch(rb"[0-9a-f]{40}", oid)),
                "Unexpected historical fixture source blob identifier")
        allowed.add(oid)
    require(allowed, "No historical source blobs found")

    # Stream Git's batch protocol instead of materializing source/history on
    # disk or collecting its bytes in one unbounded in-memory subprocess call.
    process = subprocess.Popen(
        ["git", "-C", str(ROOT), "cat-file", "--batch"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL
    )
    write_failure = []
    def supply() -> None:
        try:
            for oid in oids:
                process.stdin.write(oid + b"\n")
            process.stdin.close()
        except (BrokenPipeError, OSError) as e:
            write_failure.append(type(e).__name__)
    feeder = threading.Thread(target=supply, daemon=True)
    feeder.start()
    total_blobs, matches_in_blobs, wrong = 0, 0, 0
    wrong_paths = []
    wrong_oids: set[bytes] = set()
    try:
        for expected in oids:
            header = process.stdout.readline()
            parts = header.strip().split()
            require(len(parts) == 3 and parts[0] == expected and
                    parts[1] in (b"blob", b"tree", b"commit", b"tag") and
                    parts[2].isdigit(), "Git batch object header mismatch")
            length = int(parts[2])
            require(0 <= length <= 128 * 1024 * 1024,
                    "Oversized history object requires separate audit")
            remaining = length
            tail = b""
            found = False
            while remaining:
                chunk = process.stdout.read(min(65536, remaining))
                require(chunk, "Truncated Git batch object")
                if fixture in tail + chunk:
                    found = True
                tail = (tail + chunk)[-(len(fixture) - 1):]
                remaining -= len(chunk)
            require(process.stdout.read(1) == b"\n", "Invalid Git batch framing")
            if parts[1] == b"blob":
                total_blobs += 1
                if found:
                    matches_in_blobs += 1
                    if expected not in allowed:
                        wrong += 1
                        wrong_oids.add(expected)
                        raw = representative_paths.get(expected, b"")
                        # Git tree paths are already public, but do not emit
                        # arbitrary characters, source bytes or fixture text.
                        if (raw and len(raw) <= 200 and
                            re.fullmatch(rb"[A-Za-z0-9._/+-]+", raw)):
                            wrong_paths.append(raw.decode("ascii"))
                        else:
                            wrong_paths.append("<path-redacted>")
            else:
                require(not found, "Synthetic fixture in unexpected Git metadata")
        feeder.join(timeout=30)
        require(not feeder.is_alive() and not write_failure,
                "Historical Git scanner writer did not finish")
        require(process.wait(timeout=30) == 0,
                "Git batch history audit failed")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=5)
    require(total_blobs > 0 and matches_in_blobs > 0,
            "Historical fixture audit scanned no relevant source")
    if wrong:
        verify_reachable_tree_paths(wrong_oids)
        for path in sorted(set(wrong_paths))[:15]:
            print("XERJ_HISTORICAL_FIXTURE_EXTRA_PATH: " + path,
                  file=sys.stderr)
        print(f"XERJ_HISTORICAL_FIXTURE_EXTRA_DISTINCT_BLOBS: {wrong}",
              file=sys.stderr)
    require(wrong == 0,
            "Known synthetic signing fixture appears outside historical origin blobs")
    print(
        "XERJ_REACHABLE_GIT_JWT_FIXTURE_EXACT_REUSE_PASS "
        f"objects={len(oids)} blobs={total_blobs} "
        f"origin_versions={len(allowed)} matched_blobs={matches_in_blobs} "
        "extra_matching_blobs=0 unreachable_history_checked=NO "
        "other_secret_formats_checked=NO runtime_reuse_proven_absent=NO "
        "human_review=PENDING publication=NOT_AUTHORIZED"
    )

if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        # Every ValueError message above is a fixed, literal classification,
        # never an original Git/source string or signing value.
        print("XERJ_REACHABLE_HISTORY_FIXTURE_REVIEW_BLOCKED: " +
              str(error)[:140], file=sys.stderr)
        raise SystemExit(1)
    except (KeyError, OSError, subprocess.CalledProcessError,
            subprocess.TimeoutExpired) as error:
        # Do not print subprocess command args, source or fixture bytes.
        print("XERJ_REACHABLE_HISTORY_FIXTURE_REVIEW_BLOCKED: " +
              type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
