#!/usr/bin/env python3
"""Deterministically validate the Stage 34 repository constitution.

This validator checks repository truth/documentation invariants only. It does not
claim or simulate physical-PC validation.
"""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs" / "master-build-spec.md"
ROADMAP = ROOT / "docs" / "master-roadmap.md"
README = ROOT / "README.md"


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def main() -> int:
    errors: list[str] = []

    for path in (SPEC, ROADMAP, README):
        require(path.is_file(), f"missing required file: {path.relative_to(ROOT)}", errors)

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    spec = SPEC.read_text(encoding="utf-8")
    roadmap = ROADMAP.read_text(encoding="utf-8")
    readme = README.read_text(encoding="utf-8")

    required_spec_fragments = (
        "Stage 34 / 34",
        "BLOCKED_PENDING_HARDWARE",
        "feature/syntra-aetheris-foundation-v2",
        "Syntra",
        "Aetheris",
        "ZERO-COST",
        "PRIVATE",
        "STOP > TAKE_CONTROL > PAUSE > NORMAL",
        "UNDERSTAND → PLAN → CHECK RULES → ASSESS RISK",
        "APPROVE WHEN REQUIRED",
        "EXECUTE → VERIFY → RECORD → LEARN → REPORT",
        "OwnerRuleService",
        "OwnerRuleCompilerService",
        "ALLOW` means eligible to execute",
        "hosted CI is repository evidence",
        "no unrestricted privileged host executor",
        "no live-money execution",
        "Canonical validation gates",
        "Definition of done for the eventual physical product",
        "Continuation prompt",
        "Final Stage 34 acceptance criteria",
    )
    for fragment in required_spec_fragments:
        require(fragment in spec, f"master build spec missing invariant: {fragment!r}", errors)

    require("- [x] Stage 34 — Master build prompt" in roadmap,
            "master roadmap does not mark Stage 34 complete", errors)
    require("Stage 34 / 34" in readme,
            "README does not report Stage 34 / 34", errors)
    require("BLOCKED_PENDING_HARDWARE" in readme,
            "README lost the physical-machine truth boundary", errors)
    require("docs/master-build-spec.md" in readme,
            "README does not link the canonical master build spec", errors)

    forbidden_claims = (
        "Current physical-machine status: **`VALIDATED`**",
        "Current physical-machine status: **`PHYSICAL_PC_VALIDATED`**",
        "live-money execution is enabled",
        "unrestricted privileged host execution is validated",
        "hosted CI proves the target physical PC",
    )
    combined = "\n".join((spec, roadmap, readme)).lower()
    for claim in forbidden_claims:
        require(claim.lower() not in combined,
                f"forbidden unsupported completion claim present: {claim!r}", errors)

    require("{{" not in spec and "}}" not in spec,
            "master build spec contains unresolved template markers", errors)

    if errors:
        print("Stage 34 master build specification validation FAILED")
        for error in errors:
            print(f" - {error}")
        return 1

    print("Stage 34 master build specification validation PASSED")
    print(" - roadmap: 34 / 34 repository stages")
    print(" - physical machine: BLOCKED_PENDING_HARDWARE")
    print(" - truth boundary: hosted CI != physical-PC validation")
    return 0


if __name__ == "__main__":
    sys.exit(main())
