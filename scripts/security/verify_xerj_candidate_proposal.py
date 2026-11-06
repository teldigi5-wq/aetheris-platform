#!/usr/bin/env python3
"""Preflight only for a proposed update to an existing XERJ author candidate.

Mirrors the upstream XERJ corpus-hub sparse-path constraints from the pinned
validator revision. This is NOT the upstream full registry validator and is
intentionally incapable of granting human review or external publication.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
PROPOSAL = ROOT / "docs/integrations/xerj-upstream-amendment-draft.json"
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
SHA = re.compile(r"^[a-f0-9]{40}$")
ILLEGAL = set("*?[]!#:")  # Upstream validator on XERJ corpus-hub
REAL_LIVE_USE = {"adapt-with-attribution", "approach-only", "mixed"}


def valid_path(value: object) -> bool:
    if not isinstance(value, str) or not value or value != value.strip():
        return False
    if len(value.encode()) > 240 or value.startswith(("/", "\\", "-")):
        return False
    if re.match(r"^[A-Za-z]:", value):
        return False
    if "\\" in value or any(ord(c) < 32 or ord(c) == 127 for c in value):
        return False
    return all(
        segment not in {"", ".", ".."}
        and not segment.startswith("-")
        and not any(c in ILLEGAL or c.isspace() for c in segment)
        for segment in value.rstrip("/").split("/")
    )


def require(ok: bool, description: str) -> None:
    if not ok:
        raise ValueError(description)


def main() -> None:
    # Important negative tests: absolute paths, escaping, sparse patterns and
    # an absent review block must never be accepted as a live manifest.
    for path in ("/etc", "../secrets", "docs/../../secrets",
                 "src/*", "src/!ignore", "src/foo bar", "C:/Windows",
                 "docs//sub", "-options", "src/-danger", "src\\private"):
        require(not valid_path(path), "sparse path gate regression")
    for path in ("docs/security", "identity-service/src/main"):
        require(valid_path(path), "valid sparse paths unexpectedly rejected")

    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    d = json.loads(PROPOSAL.read_text(encoding="utf-8"))
    require(d["status"] == "DRAFT_UNREVIEWED_NOT_SUBMITTABLE",
            "Proposal must not be represented as ready for publication")
    require(d["corpus"] == "teldigi5-wq--aetheris-platform",
            "Must amend the existing XERJ author candidate, not duplicate it")
    require(d["upstream_candidate_path"] ==
            "tools/xerj-code/hub/candidates/authors/teldigi5-wq--aetheris-platform.json",
            "Unexpected XERJ target path")
    require(SHA.fullmatch(d["upstream_base_sha"]) is not None,
            "Upstream review checkpoint must be full immutable SHA")
    repo = d["repo"]
    require(repo["repo"] == "aetheris-platform" and
            repo["url"] == plan["source"]["url"],
            "Repository provenance differs from pinned-source plan")
    require(SHA.fullmatch(repo["sha"]) is not None and
            repo["sha"] == plan["source"]["sha"],
            "Pinned source SHA drifted")
    paths = repo["paths"]
    require(isinstance(paths, list) and 0 < len(paths) <= 32 and
            len(paths) == len(set(paths)) and all(map(valid_path, paths)),
            "Unsafe or changed XERJ sparse source directories")
    require(paths == plan["hub_reproducible_paths"], "Proposal and exact Hub sparse paths disagree")
    require(repo["review"]["use"] == "UNREVIEWED",
            "Only a person who reviewed the licence can set reuse approval")
    require(repo["review"]["use"] not in REAL_LIVE_USE,
            "This candidate must never pass the live XERJ review gate")
    for name in ("spdx", "by", "at"):
        require(repo["review"].get(name) is None,
                "A reviewer identity, date or SPDX conclusion was fabricated")
    for name in ("indexed_files", "indexed_bytes", "cloned_at", "consent_receipt"):
        require(d[name] is None,
                "Unknown index/consent/provenance metadata must stay null")
    require(not any(d["publication"].values()),
            "Owner, licence, maintainer or external submission permission missing")
    require(plan["publication"]["hub_submission_authorized"] is False,
            "The source plan cannot authorize Hub publication")
    print(
        "XERJ_EXISTING_CANDIDATE_AMENDMENT_PREFLIGHT_PASS "
        f"pin={repo['sha']} paths={len(paths)} "
        "human_review=UNREVIEWED publication=NOT_AUTHORIZED"
    )


if __name__ == "__main__":
    try:
        main()
    except (OSError, KeyError, TypeError, ValueError, AssertionError) as error:
        # The proposal carries no secrets, but avoid dumping raw JSON.
        print(f"XERJ_EXISTING_CANDIDATE_PREFLIGHT_FAILED: {type(error).__name__}",
              file=sys.stderr)
        raise SystemExit(1)
