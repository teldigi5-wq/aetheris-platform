from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "check_dependency_lockdown.py"
SPEC = importlib.util.spec_from_file_location("check_dependency_lockdown_ownership", MODULE_PATH)
lockdown = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(lockdown)


class CoreDependencyOwnershipTests(unittest.TestCase):
    def test_default_invocation_matches_platform_core_ownership(self) -> None:
        result = subprocess.run(
            [sys.executable, str(MODULE_PATH)],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("dependency lockdown (core): PASS", result.stdout)

    def test_extracted_runtime_scope_is_not_accepted_by_platform_verifier(self) -> None:
        result = subprocess.run(
            [sys.executable, str(MODULE_PATH), "--scope", "ai-runtime"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid choice", result.stderr)

    def test_runtime_owned_source_leak_fails_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            (base / "aetheris-quant").mkdir()
            errors = lockdown.runtime_source_boundary_errors(base)
        self.assertEqual(
            ["runtime-owned source leaked into platform: aetheris-quant"],
            errors,
        )

    def test_clean_platform_root_has_no_runtime_source_boundary_errors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            errors = lockdown.runtime_source_boundary_errors(Path(directory))
        self.assertEqual([], errors)


if __name__ == "__main__":
    unittest.main()
