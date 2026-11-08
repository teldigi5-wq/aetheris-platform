#!/usr/bin/env python3
"""Machine-check an exact unapproved XERJ per-file owner-review inventory."""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "docs/integrations/xerj-source-scope.json"
REVIEW = ROOT / "docs/integrations/xerj-44-file-review-inventory.md"
LINE = re.compile(r"^- \[ \] \x60([^\x60]+)\x60 — ([0-9]+) B · \x60([a-f0-9]{40})\x60$")
TREE = re.compile(rb"^(100644|100755) blob ([a-f0-9]{40}) ([0-9]+)\t(.+)$")


def require(ok: bool, description: str) -> None:
    if not ok:
        raise ValueError(description)


def inventory(lines: list[str]) -> dict[str, tuple[str, int]]:
    seen: dict[str, tuple[str, int]] = {}
    for line in lines:
        require(not re.match(r"^- \[[xX]\] ", line),
                "Unverified human approval in inventory")
        if not line.startswith("- [ ] " + chr(96)):
            continue
        match = LINE.fullmatch(line)
        require(match is not None, "Malformed inventory entry")
        path, size, sha = match.groups()
        require(path not in seen, "Duplicate inventory entry")
        require(0 < int(size) <= 10_000_000, "Invalid blob size")
        seen[path] = (sha, int(size))
    return seen


def main() -> None:
    testline = "- [ ] " + chr(96) + "src/main/App.java" + chr(96) \
        + " — 17 B · " + chr(96) + "a" * 40 + chr(96)
    require(inventory([testline]) ==
            {"src/main/App.java": ("a" * 40, 17)}, "Positive parser test failed")
    for bad in ([testline, testline],
                [testline.replace("[ ]", "[x]")],
                [testline.replace("a" * 40, "g" * 40)]):
        try:
            inventory(bad)
        except ValueError:
            pass
        else:
            raise ValueError("Negative parser test incorrectly passed")
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        print("XERJ_REVIEW_INVENTORY_NEGATIVE_SELF_TESTS_PASS")
        return
    require(len(sys.argv) == 1, "Unexpected args")
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    require(plan["publication"]["hub_submission_authorized"] is False,
            "Publication authorization cannot be inferred from inventory")
    pin, scopes = plan["source"]["sha"], plan["paths"]
    require(bool(re.fullmatch(r"[0-9a-f]{40}", pin)) and
            len(scopes) == 6 and len(scopes) == len(set(scopes)),
            "Changed source scope")
    doc = REVIEW.read_text(encoding="utf-8")
    require("Status: UNREVIEWED" in doc and
            "NO PUBLICATION CONSENT" in doc and
            "Stages 3–6 OPEN" in doc,
            "Human refusal context missing")
    observed = inventory(doc.splitlines())
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-tree", "-r", "-l", pin, "--", *scopes],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
        check=False, timeout=60
    )
    require(result.returncode == 0 and len(result.stdout) <= 8 * 1024 * 1024,
            "Pinned source Git inventory failed")
    expected: dict[str, tuple[str, int]] = {}
    for line in result.stdout.splitlines():
        match = TREE.fullmatch(line)
        require(match is not None, "Unexpected source tree entry")
        _, sha, size, raw_path = match.groups()
        path = raw_path.decode("utf-8")
        require(path not in expected and
                any(path.startswith(prefix + "/") for prefix in scopes),
                "Unapproved source path")
        expected[path] = (sha.decode("ascii"), int(size))
    require(len(expected) == plan["expected_tree"]["files"] == 44,
            "Review inventory count differs from selected Git files")
    require(sum(size for _, size in expected.values()) ==
            plan["expected_tree"]["bytes"] == 75323,
            "Review inventory byte count differs from Git blobs")
    require(observed == expected,
            "Review inventory has changed Git names, hashes or sizes")
    print("XERJ_HUMAN_REVIEW_INVENTORY_EXACT_PIN_PASS "
          "files=44 git_blob_bytes=75323 human_reviews=0 "
          "licence=PENDING privacy=PENDING publication=NOT_AUTHORIZED")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError,
            subprocess.TimeoutExpired) as error:
        print("XERJ_HUMAN_REVIEW_INVENTORY_BLOCKED: "
              + type(error).__name__, file=sys.stderr)
        raise SystemExit(1)
