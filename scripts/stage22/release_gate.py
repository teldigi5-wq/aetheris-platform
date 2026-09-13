#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "stage22" / "release-gate.json"
MAX_SCAN_BYTES = 2_000_000

HIGH_CONFIDENCE = [
    ("pem-private-key", re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{40,}", re.MULTILINE)),
    ("aws-access-key", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("github-classic-token", re.compile(r"\bghp_[A-Za-z0-9]{30,}\b")),
    ("github-fine-grained-token", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{40,}\b")),
    ("google-api-key", re.compile(r"\bAIza[0-9A-Za-z_-]{35}\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
]
GENERIC_SECRET = re.compile(
    r"(?im)\b(?:api[_-]?secret|secret[_-]?key|client[_-]?secret|private[_-]?key|binance[_-]?(?:testnet[_-]?)?api[_-]?secret)"
    r"\s*[:=]\s*[\"']?([A-Za-z0-9+/_=-]{24,})[\"']?"
)
PLACEHOLDER_WORDS = ("example", "dummy", "test", "changeme", "replace", "placeholder", "your", "xxxx", "sample")


def tracked_files() -> list[str]:
    proc = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, check=True, stdout=subprocess.PIPE)
    return [p.decode("utf-8") for p in proc.stdout.split(b"\0") if p]


def forbidden_path(path: str) -> str | None:
    p = Path(path)
    name = p.name.lower()
    if name == ".env":
        return "tracked-.env"
    if name in {"id_rsa", "id_ed25519", "private.pem", "private.key"}:
        return "tracked-private-key-file"
    if p.suffix.lower() in {".p12", ".pfx"}:
        return "tracked-private-key-container"
    return None


def generic_scan_allowed(path: str) -> bool:
    lowered = path.lower()
    return not (
        "/src/test/" in lowered
        or lowered.startswith("docs/")
        or lowered.endswith(".example")
        or lowered.startswith("scripts/stage22/")
    )


def main() -> int:
    findings: list[dict[str, str]] = []
    files = tracked_files()
    for rel in files:
        reason = forbidden_path(rel)
        if reason:
            findings.append({"path": rel, "rule": reason})
            continue
        path = ROOT / rel
        if not path.is_file() or path.stat().st_size > MAX_SCAN_BYTES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for rule, pattern in HIGH_CONFIDENCE:
            if pattern.search(text):
                findings.append({"path": rel, "rule": rule})
        if generic_scan_allowed(rel):
            for match in GENERIC_SECRET.finditer(text):
                value = match.group(1).lower()
                if not any(word in value for word in PLACEHOLDER_WORDS):
                    findings.append({"path": rel, "rule": "literal-secret-assignment"})
                    break

    unique = {(f["path"], f["rule"]) for f in findings}
    normalized = [{"path": path, "rule": rule} for path, rule in sorted(unique)]
    result = {
        "schemaVersion": 1,
        "trackedFileCount": len(files),
        "findingCount": len(normalized),
        "findings": normalized,
        "secretValuesEmitted": False,
        "status": "PASS" if not normalized else "FAIL",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if normalized:
        print(json.dumps(result, indent=2))
        return 1
    print(f"Stage 22 release gate PASS ({len(files)} tracked files checked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
