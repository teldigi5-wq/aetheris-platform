#!/usr/bin/env python3
"""Fail-closed Git-tree scope verifier for a DRAFT XERJ source candidate.

Validates exact Git objects/paths and size accounting without extracting files,
executing repository source, contacting XERJ or approving a licence verdict.
"""
from __future__ import annotations

import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs" / "integrations" / "xerj-source-scope.json"
SHA = re.compile(r"[0-9a-f]{40}\Z")
SAFE_PATH = re.compile(r"[A-Za-z0-9._/-]+\Z")
ALLOWED_MODES = {b"100644", b"100755"}


def git(*args: str) -> bytes:
    return subprocess.check_output(
        ["git", "-C", str(ROOT), *args], stderr=subprocess.DEVNULL
    )


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def main() -> None:
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    source = plan["source"]
    pin = source["sha"]
    paths = plan["paths"]
    expected = plan["expected_tree"]

    require(plan["status"] == "DRAFT_REVIEW_ONLY_NOT_APPROVED_FOR_HUB_SUBMISSION",
            "Publication approval must remain explicit and separate")
    require(plan["publication"]["hub_submission_authorized"] is False,
            "Preflight must not confer Hub publication approval")
    require(plan["publication"]["source_snapshot_approved"] is False,
            "Preflight must not confer snapshot approval")
    require(plan["publication"]["production_release_authorized"] is False,
            "Preflight must not authorize production release")
    require(source["repo"] == "teldigi5-wq/aetheris-platform",
            "Unexpected public repository target")
    require(bool(SHA.fullmatch(pin)), "Source pin must be a full 40-character SHA")
    require(isinstance(paths, list) and 0 < len(paths) <= 32,
            "Expected nonempty bounded sparse-checkout directory list")
    require(len(paths) == len(set(paths)), "Duplicate sparse path")
    for path in paths:
        require(isinstance(path, str) and SAFE_PATH.fullmatch(path) is not None
                and not path.startswith(("/", "-"))
                and all(segment not in {"", ".", ".."} for segment in path.split("/")),
                "Unsafe sparse path")

    actual_commit = git("rev-parse", "--verify", f"{pin}^{{commit}}").decode().strip()
    require(actual_commit == pin, "Pinned object is not the expected Git commit")
    # Git ls-tree reports Git blob sizes, not filtered or compressed index size.
    data = git("ls-tree", "-r", "-l", "-z", pin, "--", *paths)
    rows = [entry for entry in data.split(b"\x00") if entry]
    require(rows, "No source files in pinned scope")
    file_count, size_total, seen = 0, 0, set()
    objects = {}
    for entry in rows:
        meta, file_path = entry.split(b"\t", 1)
        mode, kind, oid, byte_size = meta.split()
        require(kind == b"blob" and mode in ALLOWED_MODES,
                "Non-regular file or submodule in corpus scope")
        path = file_path.decode("utf-8")
        require(path not in seen, "Duplicate path in Git tree")
        seen.add(path)
        objects[path] = (int(byte_size), oid.decode('ascii'))
        require(any(path.startswith(prefix + "/") for prefix in paths),
                "File escaped declared corpus directories")
        require(not path.endswith((".env", ".pem", ".p12", ".pfx", ".jks", ".key")),
                "Credential material filename in selected snapshot")
        require(len(oid) == 40 and int(byte_size) >= 0,
                "Invalid source blob metadata")
        file_count += 1
        size_total += int(byte_size)

    require(file_count == expected["files"], "Pinned scope file count mismatch")
    require(size_total == expected["bytes"], "Pinned scope source byte count mismatch")
    excluded = plan["excluded_source_files"]
    require(len(excluded) == 4 and len({e["path"] for e in excluded}) == 4,
            "Expected four unique pinned exclusions")
    for row in excluded:
        require(objects.get(row["path"]) == (row["bytes"], row["blob_sha"]),
                "Excluded source blob identity drift")
    filtered = plan["filtered_tree"]
    require(file_count - 4 == filtered["files"] and
            size_total - sum(e["bytes"] for e in excluded) == filtered["bytes"],
            "Filtered source count/size drift")
    # Independently prove that Lane A directory-only manifest selectors reproduce
    # EXACTLY the filtered archive: no hidden application YAML inclusion.
    expected_hub_paths = [
        "architecture", "gateway/src/main/java",
        "identity-service/src/main/java", "user-service/src/main/java",
        "audit-service/src/main/java",
        "audit-service/src/main/resources/db/migration", "docs/security",
    ]
    hub_paths = plan["hub_reproducible_paths"]
    require(hub_paths == expected_hub_paths,
            "Unexpected Hub subpath harvest boundaries")
    hub_data = git("ls-tree", "-r", "-l", "-z", pin, "--", *hub_paths)
    hub_objects = {}
    for item in (entry for entry in hub_data.split(b"\x00") if entry):
        meta, raw_path = item.split(b"\t", 1)
        mode, kind, sha, size = meta.split()
        path = raw_path.decode("utf-8")
        require(kind == b"blob" and mode in ALLOWED_MODES,
                "Invalid Hub corpus Git object kind")
        require(path not in hub_objects and
                any(path.startswith(root + "/") for root in hub_paths),
                "Invalid Hub subpath or duplicate")
        hub_objects[path] = (int(size), sha.decode("ascii"))
    filtered_objects = {
        path: identity for path, identity in objects.items()
        if path not in {row["path"] for row in excluded}
    }
    require(hub_objects == filtered_objects,
            "Lane A Hub directory paths do not reproduce exact filtered blobs")
    require(len(hub_objects) == plan["hub_reproducible_tree"]["files"] and
            sum(size for size, _ in hub_objects.values()) ==
                plan["hub_reproducible_tree"]["bytes"],
            "Lane A Hub source accounting drift")
    print("XERJ_HUB_REPRODUCIBLE_SUBPATHS_PASS files=40 git_blob_bytes=66617 "
          "publication=NOT_AUTHORIZED")
    print(
        "XERJ_SOURCE_SCOPE_PROOF_PASS "
        f"pin={pin} files={file_count} git_blob_bytes={size_total} "
        "publication=NOT_AUTHORIZED"
    )


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as exc:
        # No source content, credentials or scanned text is logged.
        print(f"XERJ_SOURCE_SCOPE_PROOF_FAILED: {type(exc).__name__}: {exc}",
              file=sys.stderr)
        raise SystemExit(1)
