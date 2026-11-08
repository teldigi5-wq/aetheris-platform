"""Safely collect Docker gateway state for bounded hosted recovery diagnostics.

Only allowlisted status fields may enter public CI reports.
"""
from __future__ import annotations

import json
import subprocess
from pathlib import Path
from typing import Any


def allowlisted_state(state: dict[str, Any]) -> dict[str, Any]:
    health = state.get("Health")
    return {
        "available": True,
        "status": state.get("Status"),
        "running": state.get("Running"),
        "exit_code": state.get("ExitCode"),
        "oom_killed": state.get("OOMKilled"),
        "health_status": health.get("Status") if isinstance(health, dict) else None,
    }


def capture_gateway_state(root: Path) -> dict[str, Any]:
    """Never include container env, Docker inspect dumps or health-log output."""
    try:
        listing = subprocess.run(
            ["docker", "compose", "ps", "--all", "--quiet", "gateway"],
            cwd=root, capture_output=True, text=True, timeout=10, check=False,
        )
        if listing.returncode != 0:
            return {"available": False, "reason": "compose_ps_failed"}
        ids = [line.strip() for line in listing.stdout.splitlines() if line.strip()]
        if len(ids) != 1:
            return {"available": False, "reason": "gateway_count_not_one"}
        details = subprocess.run(
            ["docker", "inspect", "--format", "{{json .State}}", ids[0]],
            cwd=root, capture_output=True, text=True, timeout=10, check=False,
        )
        if details.returncode != 0:
            return {"available": False, "reason": "inspect_failed"}
        state = json.loads(details.stdout)
        if not isinstance(state, dict):
            return {"available": False, "reason": "invalid_state"}
        return allowlisted_state(state)
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
        return {"available": False, "reason": "state_unavailable"}
