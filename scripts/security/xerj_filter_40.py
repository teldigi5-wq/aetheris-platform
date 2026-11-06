#!/usr/bin/env python3
"""Fail-closed disposable XERJ source filter. Does not grant publication consent."""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import stat
import sys

PIN = "57ad3e0e602efa6feb275328254151b959a3499b"
ROOTS = ("architecture/", "gateway/src/main/", "identity-service/src/main/",
         "user-service/src/main/", "audit-service/src/main/", "docs/security/")
EXCLUDED = {
    "gateway/src/main/resources/application.yml": (5256, "41292689d047943c30f26e93211570a41edc48b1"),
    "identity-service/src/main/resources/application.yml": (1134, "4ee43491af1b84e45a001550e6a6a44b7c15e243"),
    "user-service/src/main/resources/application.yml": (1258, "b6894049f3b3878f047330b1e9ac042e0819a42b"),
    "audit-service/src/main/resources/application.yml": (1058, "7c16b87fa13bce3239bdf91166ff145daa71045a"),
}

class FilterError(ValueError):
    pass

def required(condition, reason):
    if not condition:
        raise FilterError(reason)

def inventory(root):
    required(root.is_dir() and not root.is_symlink(), "invalid archive directory")
    found = {}
    for base, dirs, files in os.walk(root, followlinks=False):
        for name in dirs:
            required(stat.S_ISDIR((Path(base) / name).lstat().st_mode),
                     "unexpected directory type")
        for name in files:
            entry = Path(base) / name
            metadata = entry.lstat()
            required(stat.S_ISREG(metadata.st_mode) and metadata.st_nlink == 1,
                     "unsafe file type")
            relative = entry.relative_to(root).as_posix()
            required(any(relative.startswith(prefix) for prefix in ROOTS)
                     and ".." not in relative.split("/") and relative not in found,
                     "unexpected file path")
            found[relative] = entry
    return found

def filter_source(scratch, source, destination, pin):
    required(pin == PIN, "pin mismatch")
    required(scratch.is_dir() and not scratch.is_symlink(), "invalid scratch")
    root = scratch.resolve(strict=True)
    required(source.parent.resolve(strict=True) == root and
             destination.parent.resolve(strict=True) == root,
             "input and output must be scratch children")
    required(source.name.startswith("xerj-pinned-") and
             destination.name.startswith("xerj-filtered-") and
             source != destination and not destination.exists() and
             not destination.is_symlink(), "unsafe or preexisting output")
    original = inventory(source)
    required(len(original) == 44 and sum(f.stat().st_size for f in original.values()) == 75323,
             "pinned archive count/bytes drift")
    required(set(EXCLUDED).issubset(original), "missing excluded path")
    for path, (size, blob_oid) in EXCLUDED.items():
        raw = original[path].read_bytes()
        observed = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        required(len(raw) == size and observed == blob_oid, "excluded blob changed")
    selected = {p: f for p, f in original.items() if p not in EXCLUDED}
    required(len(selected) == 40 and sum(f.stat().st_size for f in selected.values()) == 66617,
             "filtered count/bytes drift")
    created = False
    try:
        destination.mkdir(mode=0o700)
        created = True
        for path, file in sorted(selected.items()):
            target = destination / path
            target.parent.mkdir(parents=True, exist_ok=True)
            raw = file.read_bytes()
            required(len(raw) == file.stat().st_size, "input changed during copy")
            target.write_bytes(raw)
            required(target.read_bytes() == raw, "copy mismatch")
        actual = inventory(destination)
        required(set(actual) == set(selected) and
                 sum(f.stat().st_size for f in actual.values()) == 66617 and
                 not set(actual).intersection(EXCLUDED), "filtered output invalid")
    except Exception:
        if created:
            shutil.rmtree(destination)
        raise

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scratch", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-pin", required=True)
    args = parser.parse_args()
    filter_source(args.scratch, args.source, args.output, args.source_pin)
    print("XERJ_FILTERED40_PASS files=40 git_blob_bytes=66617 publication=NOT_AUTHORIZED")

if __name__ == "__main__":
    try:
        main()
    except (FilterError, OSError) as exc:
        print("XERJ_FILTERED40_FAIL: " + type(exc).__name__, file=sys.stderr)
        raise SystemExit(1)
