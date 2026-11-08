#!/usr/bin/env python3
"""Ephemeral real-XERJ corpus add --from sparse-harvest acceptance.

Checks actual release CLI materializes exactly 44 Git blobs, not the full
source repository. Never creates or submits a Hub manifest or writes
review/consent metadata to any persistent repository.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
TREE = re.compile(rb"^(100644|100755) blob ([a-f0-9]{40}) +([0-9]+)\t(.+)$")
NAME = "aetheris-xerj-sparse-review"
REPO = "aetheris-platform"


def require(ok: bool, why: str) -> None:
    if not ok:
        raise ValueError(why)


def cmd(args: list[str], *, cwd: Path | None = None,
        env: dict[str, str] | None = None, timeout: int = 120) -> bytes:
    result = subprocess.run(
        args, cwd=cwd or ROOT, env=env,
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        timeout=timeout, check=False
    )
    require(result.returncode == 0,
            "Local synthetic sparse-consumer command failed")
    require(len(result.stdout) <= 4 * 1024 * 1024,
            "Unexpected large tool output withheld")
    return result.stdout


def selected(plan: dict) -> dict[str, tuple[str, int]]:
    raw = cmd(["git", "ls-tree", "-r", "-l", plan["source"]["sha"],
               "--", *plan["paths"]])
    expected = {}
    for entry in raw.splitlines():
        m = TREE.fullmatch(entry)
        require(m is not None, "Unexpected Git source item")
        _, blob, size, path_bytes = m.groups()
        path = path_bytes.decode("utf-8")
        require(path not in expected and
                any(path.startswith(p + "/") for p in plan["paths"]),
                "Unapproved pinned source path")
        expected[path] = (blob.decode("ascii"), int(size))
    require(len(expected) == plan["expected_tree"]["files"] == 44 and
            sum(size for _, size in expected.values()) ==
            plan["expected_tree"]["bytes"] == 75323,
            "Source inventory changed unexpectedly")
    return expected


def verify(binary: Path, work: Path) -> None:
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    require(not plan["publication"]["hub_submission_authorized"] and
            not plan["publication"]["source_snapshot_approved"],
            "Publication authorization status was unexpectedly changed")
    require(plan["paths"] == [
        "architecture", "gateway/src/main", "identity-service/src/main",
        "user-service/src/main", "audit-service/src/main", "docs/security"
    ], "Unexpected XERJ sparse source directories")
    require(binary.is_file() and work.is_dir() and not work.is_symlink(),
            "Pinned XERJ executable or disposable workdir missing")
    expected = selected(plan)

    # Make a local, unadvertised-as-public bare mirror of the EXACT pin.
    # No external cloning dependency in this acceptance test: the public repo
    # was already checked out with history by this read-only CI workflow.
    bare = work / "sparse-consumer-bare.git"
    cmd(["git", "init", "--quiet", "--bare", str(bare)])
    cmd(["git", "-C", str(bare), "fetch", "--quiet",
         str(ROOT), plan["source"]["sha"]], timeout=120)
    cmd(["git", "-C", str(bare), "update-ref", "refs/heads/pinned-source",
         plan["source"]["sha"]])
    require(cmd(["git", "-C", str(bare), "rev-parse",
                 "refs/heads/pinned-source"]).decode().strip() ==
            plan["source"]["sha"], "Local Git mirror source pin mismatch")

    # This file and its made-up classification are CI ephemeral only.
    # It is intentionally NOT a valid human-reviewed Hub manifest.
    manifest = work / "sparse-consumer-UNREVIEWED-ci-only.json"
    manifest.write_text(json.dumps({
        "corpus": NAME,
        "status": "UNREVIEWED_CI_ONLY_NEVER_PUBLISH",
        "cloned_at": None,
        "repos": [{
            "repo": REPO,
            "url": bare.resolve().as_uri(),
            "sha": plan["source"]["sha"],
            "paths": plan["paths"],
            "licence": "UNREVIEWED CI-only fixture, not an owner attestation",
            "files": 44, "bytes": 75323,
            "review": {
                "spdx": None, "use": "UNREVIEWED", "by": None, "at": None,
                "note": "Synthetic local consumer test; no human licence review"
            }
        }]
    }, sort_keys=True) + "\n", encoding="utf-8")

    home = work / "sparse-consumer-home"
    home.mkdir()
    env = dict(os.environ, XERJ_CODE_HOME=str(home))
    # Capture but NEVER print CLI output, which may show filesystem paths.
    cmd([str(binary.resolve()), "corpus", "add", "--from",
         str(manifest)], env=env, timeout=180)
    checkout = home / "corpora" / NAME / REPO
    require(checkout.is_dir() and (checkout / ".git").exists(),
            "XERJ did not create the expected isolated Git checkout")
    sha = cmd(["git", "-C", str(checkout), "rev-parse", "HEAD"]).decode().strip()
    require(sha == plan["source"]["sha"],
            "XERJ checkout is not the source pin in the manifest")
    actual = {}
    for item in checkout.rglob("*"):
        if ".git" in item.relative_to(checkout).parts:
            continue
        require(not item.is_symlink(), "Unexpected symlink in XERJ worktree")
        if not item.is_file():
            continue
        rel = item.relative_to(checkout).as_posix()
        require(rel in expected, "XERJ materialized file outside selected paths")
        require(item.stat().st_size == expected[rel][1],
                "XERJ worktree file size differs from source blob")
        oid = cmd(["git", "-C", str(checkout), "hash-object",
                   "--", rel]).decode().strip()
        require(oid == expected[rel][0],
                "XERJ worktree content differs from selected source blob")
        actual[rel] = True
    require(set(actual) == set(expected) and len(actual) == 44,
            "XERJ sparse consumer did not materialize exactly 44 files")
    print("XERJ_REAL_RELEASE_CORPUS_ADD_FROM_SPARSE_CONSUMER_PASS "
          "files=44 git_blob_bytes=75323 outside_scope=0 "
          "selected_source_pin=VERIFIED human_review=UNREVIEWED "
          "publication=NOT_AUTHORIZED")


if __name__ == "__main__":
    try:
        require(len(sys.argv) == 3,
                "Provide checksum-verified binary and disposable workdir")
        verify(Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, ValueError, KeyError, TypeError,
            subprocess.TimeoutExpired) as error:
        print("XERJ_SPARSE_CONSUMER_ACCEPTANCE_BLOCKED: " +
              type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
