#!/usr/bin/env python3
"""Ensure AI-runtime-owned source trees are absent from core-only CI proofs.

Before the repository split this helper moved the four AI-runtime trees out of a
core CI workspace. After the split the same proof remains useful, but source may
already be absent in the checked-out platform tree. Both states are accepted;
the invariant is that none of the AI-runtime-owned roots exists when core CI
continues.
"""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AI_RUNTIME_PATHS = (
    "orchestrator-service",
    "workstation-agent",
    "aetheris-quant",
    "aetheris-reasoning",
)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--destination", required=True)
    args = parser.parse_args()
    destination = Path(args.destination).resolve()
    destination.mkdir(parents=True, exist_ok=True)

    moved: list[str] = []
    already_absent: list[str] = []
    for relative in AI_RUNTIME_PATHS:
        source = ROOT / relative
        if not source.exists():
            already_absent.append(relative)
            continue
        target = destination / relative
        if target.exists():
            raise SystemExit(f"isolation destination already exists: {target}")
        shutil.move(str(source), str(target))
        moved.append(relative)

    leaked = [relative for relative in AI_RUNTIME_PATHS if (ROOT / relative).exists()]
    if leaked:
        raise SystemExit(f"AI-runtime source still present after isolation: {leaked}")

    print(
        "AI-runtime source absent from core CI workspace; "
        f"moved={moved or 'none'}; already_absent={already_absent or 'none'}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
