#!/usr/bin/env python3
"""Move AI-runtime-owned source trees out of the workspace for core-only CI proofs."""

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

    for relative in AI_RUNTIME_PATHS:
        source = ROOT / relative
        if not source.exists():
            raise SystemExit(f"expected AI-runtime source is missing before isolation: {relative}")
        target = destination / relative
        if target.exists():
            raise SystemExit(f"isolation destination already exists: {target}")
        shutil.move(str(source), str(target))

    leaked = [relative for relative in AI_RUNTIME_PATHS if (ROOT / relative).exists()]
    if leaked:
        raise SystemExit(f"AI-runtime source still present after isolation: {leaked}")

    print("AI-runtime source trees isolated from core CI workspace")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
