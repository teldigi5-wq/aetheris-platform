#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "stage22" / "schema-guard.json"
TABLE = re.compile(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', re.MULTILINE)
UNSAFE_DDL = re.compile(r"(?im)^\s*(?:ddl-auto|hibernate\.ddl-auto)\s*:\s*(create|create-drop|update)\s*$")


def main() -> int:
    tables: dict[str, list[str]] = defaultdict(list)
    java_root = ROOT / "orchestrator-service" / "src" / "main" / "java"
    if java_root.exists():
        for java in sorted(java_root.rglob("*.java")):
            text = java.read_text(encoding="utf-8")
            for match in TABLE.finditer(text):
                tables[match.group(1)].append(java.relative_to(ROOT).as_posix())
    duplicates = {name: paths for name, paths in sorted(tables.items()) if len(paths) > 1}

    unsafe = []
    resources = ROOT / "orchestrator-service" / "src" / "main" / "resources"
    if resources.exists():
        for cfg in sorted(list(resources.rglob("*.yml")) + list(resources.rglob("*.yaml"))):
            text = cfg.read_text(encoding="utf-8")
            for match in UNSAFE_DDL.finditer(text):
                unsafe.append({"path": cfg.relative_to(ROOT).as_posix(), "mode": match.group(1)})

    result = {
        "schemaVersion": 1,
        "entityTableCount": len(tables),
        "duplicateTableNames": duplicates,
        "unsafeProductionDdlModes": unsafe,
        "status": "PASS" if not duplicates and not unsafe else "FAIL",
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if result["status"] != "PASS":
        print(json.dumps(result, indent=2))
        return 1
    print(f"Stage 22 schema guard PASS ({len(tables)} explicit JPA tables)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
