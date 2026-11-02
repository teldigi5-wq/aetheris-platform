#!/usr/bin/env python3
"""Read-only pinned source verification; never a release approval."""
import json
import subprocess
import sys
from pathlib import Path

COMMIT = "d44d82f19d578c1c8e32b91911374a41bcea4aa8"
COMPOSE_BLOB = "5f409670c960aa543a40948c57bcde86f54bb85e"
PATH = "docker-compose.yml"


def _git(root: Path, *args: str) -> tuple[int, str]:
    try:
        p = subprocess.run(["git", "-C", str(root), *args], capture_output=True,
                           text=True, timeout=12, check=False)
        return p.returncode, p.stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return 127, ""


def evaluate(root: Path) -> tuple[int, dict]:
    base = {"gate": "UNVERIFIED", "release": "NO_GO", "scope": "pinned-compose-only",
            "db_roles_inspected": False, "real_migrations_approved": False}
    if not root.is_dir():
        return 3, base
    rc, resolved = _git(root, "rev-parse", "--verify", f"{COMMIT}^{{commit}}")
    if rc or resolved != COMMIT:
        return 3, base
    rc, blob = _git(root, "rev-parse", "--verify", f"{COMMIT}:{PATH}")
    if rc or blob != COMPOSE_BLOB:
        return 3, base
    rc, content = _git(root, "show", f"{COMMIT}:{PATH}")
    if rc or not content or len(content) > 200_000:
        return 3, base
    # This is a narrow source-pin witness, not a general YAML policy scanner.
    expected = ["POSTGRES_USER: aetheris", "aetheris_pg:/var/lib/postgresql/data",
                "SPRING_DATASOURCE_USERNAME: aetheris"]
    if not all(value in content for value in expected):
        return 3, base
    base["gate"] = "BLOCKED_SHARED_BOOTSTRAP_ROLE"
    return 2, base


def main(argv: list[str]) -> int:
    status, report = evaluate(Path(argv[0]) if len(argv) == 1 else Path("/NONEXISTENT"))
    print(json.dumps(report, sort_keys=True, separators=(",", ":")))
    return status


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
