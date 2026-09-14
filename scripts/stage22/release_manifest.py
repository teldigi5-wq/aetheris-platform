#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return sorted(p.decode("utf-8") for p in raw.split(b"\0") if p)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/stage22/release-manifest.json")
    args = parser.parse_args()

    entries = []
    for rel in tracked_files():
        path = ROOT / rel
        if not path.is_file():
            continue
        entries.append({"path": rel, "sha256": sha256(path), "size": path.stat().st_size})
    result = {
        "schemaVersion": 1,
        "repository": "teldigi5-wq/aetheris-platform",
        "commitSha": git("rev-parse", "HEAD"),
        "treeSha": git("rev-parse", "HEAD^{tree}"),
        "trackedFileCount": len(entries),
        "files": entries,
        "deterministic": True,
    }
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Generated deterministic release manifest for {len(entries)} tracked files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
