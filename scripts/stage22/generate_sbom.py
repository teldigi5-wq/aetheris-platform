#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def add_component(items: dict[tuple, dict], component: dict) -> None:
    key = (component.get("type"), component.get("group", ""), component.get("name"), component.get("version", ""), component.get("scope", ""))
    items.setdefault(key, component)


def parse_poms(items: dict[tuple, dict]) -> None:
    for pom in sorted(ROOT.rglob("pom.xml")):
        if any(part in {"target", ".git"} for part in pom.parts):
            continue
        try:
            tree = ET.parse(pom)
        except ET.ParseError:
            continue
        root = tree.getroot()
        ns = ""
        if root.tag.startswith("{"):
            ns = root.tag.split("}", 1)[0] + "}"
        for dep in root.findall(f".//{ns}dependencies/{ns}dependency"):
            group = (dep.findtext(f"{ns}groupId") or "").strip()
            name = (dep.findtext(f"{ns}artifactId") or "").strip()
            version = (dep.findtext(f"{ns}version") or "managed").strip()
            scope = (dep.findtext(f"{ns}scope") or "runtime").strip()
            if name:
                add_component(items, {
                    "type": "library",
                    "group": group,
                    "name": name,
                    "version": version,
                    "scope": "optional" if scope in {"test", "provided"} else "required",
                    "properties": [{"name": "aetheris.source", "value": str(pom.relative_to(ROOT)).replace("\\", "/")}],
                })


def parse_requirements(items: dict[tuple, dict]) -> None:
    for req in sorted(ROOT.rglob("requirements.txt")):
        if any(part in {".venv", "venv", "target", "node_modules"} for part in req.parts):
            continue
        for raw in req.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or line.startswith("-"):
                continue
            if "==" in line:
                name, version = line.split("==", 1)
            else:
                name, version = line, "declared-unpinned"
            add_component(items, {
                "type": "library",
                "name": name.strip(),
                "version": version.strip(),
                "scope": "required",
                "properties": [{"name": "aetheris.source", "value": str(req.relative_to(ROOT)).replace("\\", "/")}],
            })


def parse_packages(items: dict[tuple, dict], warnings: list[str]) -> None:
    for pkg in sorted(ROOT.rglob("package.json")):
        if "node_modules" in pkg.parts or "dist" in pkg.parts:
            continue
        try:
            data = json.loads(pkg.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        lock = pkg.with_name("package-lock.json")
        if not lock.exists():
            warnings.append(f"Missing package-lock.json beside {pkg.relative_to(ROOT).as_posix()}")
        for section, scope in (("dependencies", "required"), ("devDependencies", "optional")):
            for name, version in sorted((data.get(section) or {}).items()):
                add_component(items, {
                    "type": "library",
                    "name": name,
                    "version": str(version),
                    "scope": scope,
                    "properties": [
                        {"name": "aetheris.source", "value": str(pkg.relative_to(ROOT)).replace("\\", "/")},
                        {"name": "aetheris.lockfilePresent", "value": str(lock.exists()).lower()},
                    ],
                })


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="build/stage22/sbom.cdx.json")
    args = parser.parse_args()

    components: dict[tuple, dict] = {}
    warnings: list[str] = []
    parse_poms(components)
    parse_requirements(components)
    parse_packages(components, warnings)
    commit = git("rev-parse", "HEAD")
    serial = uuid.uuid5(uuid.NAMESPACE_URL, f"https://github.com/teldigi5-wq/aetheris-platform@{commit}")
    body = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:{serial}",
        "version": 1,
        "metadata": {
            "component": {"type": "application", "name": "aetheris-platform", "version": commit[:12]},
            "properties": [
                {"name": "aetheris.generatedFromCommit", "value": commit},
                {"name": "aetheris.deterministic", "value": "true"},
            ],
        },
        "components": [components[k] for k in sorted(components)],
        "properties": [
            {"name": "aetheris.warningCount", "value": str(len(warnings))},
            {"name": "aetheris.warningsSha256", "value": hashlib.sha256("\n".join(sorted(warnings)).encode()).hexdigest()},
        ],
        "aetherisWarnings": sorted(warnings),
    }
    out = ROOT / args.output
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(body, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Generated deterministic Stage 22 SBOM with {len(body['components'])} components and {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
