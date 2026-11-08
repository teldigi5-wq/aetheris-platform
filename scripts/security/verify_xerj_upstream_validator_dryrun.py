#!/usr/bin/env python3
"""CI-only XERJ upstream validator dry run; never submits a Hub manifest.

Checks the existing upstream author candidate and proves the actual pinned
corpus-hub validator REFUSES an explicitly unreviewed live manifest.
Does not pretend to perform human licensing/consent review or index a pack.
"""
from __future__ import annotations

import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
PROPOSAL = ROOT / "docs/integrations/xerj-upstream-amendment-draft.json"
PINNED_OLD_CANDIDATE = "908ac0a52b8c912af31195e60e7403cc6b61e691"
VALIDATOR = "tools/xerj-code/hub/validate_hub.py"


def insist(ok: bool, reason: str) -> None:
    if not ok:
        raise ValueError(reason)


def main() -> None:
    insist(len(sys.argv) == 2, "Expected one isolated upstream checkout path")
    upstream = Path(sys.argv[1]).resolve(strict=True)
    manifest_dir = upstream / "tools/xerj-code/hub"
    actual = subprocess.check_output(
        ["git", "-C", str(upstream), "rev-parse", "HEAD"],
        stderr=subprocess.DEVNULL, timeout=10
    ).decode("ascii").strip()

    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    proposal = json.loads(PROPOSAL.read_text(encoding="utf-8"))
    insist(actual == proposal["upstream_base_sha"],
           "Unreviewed change to upstream XERJ validator revision")
    insist(plan["publication"]["source_snapshot_approved"] is False
           and plan["publication"]["hub_submission_authorized"] is False,
           "No public submission authorization may be inferred")
    insist(proposal["status"] == "DRAFT_UNREVIEWED_NOT_SUBMITTABLE"
           and proposal["repo"]["review"]["use"] == "UNREVIEWED",
           "Proposal must remain explicitly unreviewed")
    insist(all(proposal["repo"]["review"].get(field) is None
               for field in ("spdx", "by", "at")),
           "Reviewer must not be fabricated")
    insist(all(proposal.get(field) is None
               for field in ("indexed_files", "indexed_bytes",
                             "cloned_at", "consent_receipt")),
           "Unknown actual index statistics or consent must remain unknown")
    insist(not any(proposal["publication"].values()),
           "No new owner, maintainer or licence approval has occurred")
    insist(proposal["repo"]["sha"] == plan["source"]["sha"]
           and proposal["repo"]["paths"] == plan["hub_reproducible_paths"],
           "Pinned 40-file Hub scope must match amendment")
    target_path = proposal["upstream_candidate_path"]
    insist(target_path ==
           "tools/xerj-code/hub/candidates/authors/teldigi5-wq--aetheris-platform.json",
           "Must update existing candidate rather than adding duplicate")
    candidate = json.loads((upstream / target_path).read_text(encoding="utf-8"))
    insist(candidate.get("corpus") == proposal["corpus"]
           and candidate.get("status") == "draft"
           and candidate.get("cloned_at") is None
           and len(candidate.get("repos", [])) == 1,
           "The existing upstream candidate changed unexpectedly")
    entry = candidate["repos"][0]
    insist(entry.get("sha") == PINNED_OLD_CANDIDATE
           and entry.get("url") == proposal["repo"]["url"]
           and not entry.get("paths"),
           "Old unrestricted upstream candidate changed; reevaluate privacy")
    # The original consent is retained as external *historical* data, never
    # applied to the proposed newer, narrower source pin.
    insist(entry.get("review", {}).get("consent_receipt") is not None,
           "Original author-candidate provenance changed; manual re-review")

    # Run the REAL upstream stdlib validator in its own checkout, unchanged.
    baseline = subprocess.run(
        [sys.executable, str(upstream / VALIDATOR)],
        cwd=upstream, capture_output=True, text=True,
        timeout=75, check=False
    )
    insist(baseline.returncode == 0,
           "Pinned upstream XERJ registry fails its own baseline validator")

    # This file exists ONLY in disposable runner checkout, never in the
    # Aetheris repo, GitHub PR, public upstream Hub or a submitted corpus.
    negative_name = "aetheris-xerj-ci-unreviewed-negative-only"
    negative = manifest_dir / (negative_name + ".json")
    insist(not negative.exists(), "Existing unexpected upstream test manifest")
    fixture = {
        "corpus": negative_name,
        "cloned_at": None,
        "repos": [{
            "repo": "aetheris-platform",
            "url": proposal["repo"]["url"],
            "licence": "repository-declared Apache-2.0, not human reviewed",
            "sha": plan["source"]["sha"],
            "paths": plan["hub_reproducible_paths"],
            "files": plan["hub_reproducible_tree"]["files"],
            "bytes": plan["hub_reproducible_tree"]["bytes"],
            "review": {
                "spdx": "Apache-2.0",
                "use": "UNREVIEWED",
                "by": "negative-fixture-not-a-reviewer",
                "at": "1970-01-01",
                "note": "CI negative fixture only. No person reviewed this snapshot."
            }
        }]
    }
    try:
        negative.write_text(json.dumps(fixture) + "\n", encoding="utf-8")
        rejected = subprocess.run(
            [sys.executable, str(upstream / VALIDATOR)],
            cwd=upstream, capture_output=True, text=True,
            timeout=75, check=False
        )
        # Do not print validator output; might quote a private registry value.
        insist(rejected.returncode != 0
               and "review.use must be one of" in
               (rejected.stdout + rejected.stderr),
               "Upstream validator did not specifically reject unreviewed Lane A")
    finally:
        negative.unlink(missing_ok=True)

    print(
        "XERJ_UPSTREAM_PINNED_REGISTRY_BASELINE_PASS "
        "XERJ_UNREVIEWED_LIVE_MANIFEST_DENIAL_PASS "
        "existing_candidate=UNCHANGED "
        "new_hub_manifest=NONE "
        "human_review=PENDING publication=NOT_AUTHORIZED"
    )


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, TypeError,
            subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        # Never echo source, an upstream registry record or a test secret.
        print("XERJ_UPSTREAM_SCHEMA_DRYRUN_BLOCKED: "
              + type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
