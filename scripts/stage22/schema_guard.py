#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build" / "stage22" / "schema-guard.json"
TABLE = re.compile(r'@Table\s*\(\s*name\s*=\s*"([^"]+)"', re.MULTILINE)
UNSAFE_YAML_DDL = re.compile(
    r"(?im)^\s*(?:ddl-auto|hibernate\.ddl-auto|spring\.jpa\.hibernate\.ddl-auto)\s*:\s*"
    r"(create|create-drop|update)\s*(?:#.*)?$"
)
UNSAFE_PROPERTIES_DDL = re.compile(
    r"(?im)^\s*spring\.jpa\.hibernate\.ddl-auto\s*=\s*(create|create-drop|update)\s*(?:#.*)?$"
)


def _relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def discover_main_roots(root: Path) -> tuple[list[Path], list[Path]]:
    """Discover platform-owned top-level Java service main roots.

    Runtime-owned source is intentionally absent from this repository after the
    extraction. Discovering current service roots prevents the guard from
    silently succeeding against a deleted service path.
    """

    java_roots = sorted(path for path in root.glob("*/src/main/java") if path.is_dir())
    resource_roots = sorted(path for path in root.glob("*/src/main/resources") if path.is_dir())
    return java_roots, resource_roots


def scan_repository(root: Path = ROOT) -> dict[str, Any]:
    java_roots, resource_roots = discover_main_roots(root)
    scan_errors: list[str] = []
    if not java_roots:
        scan_errors.append("no platform src/main/java roots discovered")
    if not resource_roots:
        scan_errors.append("no platform src/main/resources roots discovered")

    tables: dict[str, list[str]] = defaultdict(list)
    java_file_count = 0
    for java_root in java_roots:
        for java in sorted(java_root.rglob("*.java")):
            java_file_count += 1
            text = java.read_text(encoding="utf-8")
            for match in TABLE.finditer(text):
                tables[match.group(1)].append(_relative(java, root))

    duplicates = {name: paths for name, paths in sorted(tables.items()) if len(paths) > 1}

    unsafe: list[dict[str, str]] = []
    resource_config_count = 0
    for resources in resource_roots:
        configs = sorted(
            [
                *resources.rglob("*.yml"),
                *resources.rglob("*.yaml"),
                *resources.rglob("*.properties"),
            ]
        )
        for cfg in configs:
            resource_config_count += 1
            text = cfg.read_text(encoding="utf-8")
            patterns = (UNSAFE_PROPERTIES_DDL,) if cfg.suffix == ".properties" else (UNSAFE_YAML_DDL,)
            for pattern in patterns:
                for match in pattern.finditer(text):
                    unsafe.append({"path": _relative(cfg, root), "mode": match.group(1)})

    return {
        "schemaVersion": 2,
        "javaRoots": [_relative(path, root) for path in java_roots],
        "resourceRoots": [_relative(path, root) for path in resource_roots],
        "javaFileCount": java_file_count,
        "resourceConfigFileCount": resource_config_count,
        "entityTableCount": len(tables),
        "duplicateTableNames": duplicates,
        "unsafeProductionDdlModes": unsafe,
        "scanErrors": scan_errors,
        "status": "PASS" if not duplicates and not unsafe and not scan_errors else "FAIL",
    }


def main() -> int:
    result = scan_repository(ROOT)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if result["status"] != "PASS":
        print(json.dumps(result, indent=2))
        return 1
    print(
        "Stage 22 schema guard PASS "
        f"({result['entityTableCount']} explicit JPA tables; "
        f"{result['javaFileCount']} Java files; "
        f"{result['resourceConfigFileCount']} main-resource configs)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
