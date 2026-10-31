"""Offline, synthetic regression tests for the fail-closed Gitleaks range wrapper."""
from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/security/scan_new_secrets.sh"


class SecretScanCiTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="aetheris-secret-scan-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("config", "user.email", "synthetic@example.invalid")
        self.git("config", "user.name", "Synthetic Audit")
        (self.repo / "fixture.txt").write_text("initial synthetic content\n")
        self.git("add", "fixture.txt")
        self.git("commit", "-qm", "synthetic base")
        self.primary_branch = self.git("symbolic-ref", "--short", "HEAD").strip()
        self.base = self.git("rev-parse", "HEAD").strip()
        (self.repo / "fixture.txt").write_text("changed synthetic content\n")
        self.git("add", "fixture.txt")
        self.git("commit", "-qm", "synthetic change")
        self.log = self.root / "scan-args.txt"
        self.fake_scanner = self.root / "gitleaks"
        self.fake_scanner.write_text(
            "#!/bin/sh\n"
            "printf '%s\\n' \"$@\" > \"$AUDIT_ARGUMENTS\"\n"
            "exit \"${SCANNER_EXIT_CODE:-0}\"\n"
        )
        self.fake_scanner.chmod(0o700)

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.repo, text=True)

    def scan(self, base, *, scanner_exit="0"):
        env = os.environ.copy()
        env["GITLEAKS_BIN"] = str(self.fake_scanner)
        env["AUDIT_ARGUMENTS"] = str(self.log)
        env["SCANNER_EXIT_CODE"] = scanner_exit
        return subprocess.run(
            ["bash", str(SCRIPT), base], cwd=self.repo, env=env,
            capture_output=True, text=True, check=False,
        )

    def test_scans_exact_new_commit_range_without_exclusions(self):
        result = self.scan(self.base)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ["git", "--redact", "--ignore-gitleaks-allow", f"--log-opts={self.base}..HEAD", "."],
            self.log.read_text().splitlines(),
        )

    def test_invalid_or_missing_base_fails_closed(self):
        for base in ("", "0" * 40, "not-a-commit", "a" * 39):
            with self.subTest(base=base):
                self.log.unlink(missing_ok=True)
                result = self.scan(base)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse(self.log.exists())

    def test_unknown_base_fails_closed(self):
        result = self.scan("f" * 40)
        self.assertNotEqual(0, result.returncode)

    def test_nonancestor_base_fails_closed(self):
        self.git("checkout", "-qb", "side", self.base)
        (self.repo / "other.txt").write_text("side branch synthetic content\n")
        self.git("add", "other.txt")
        self.git("commit", "-qm", "synthetic side")
        side_sha = self.git("rev-parse", "HEAD").strip()
        self.git("checkout", "-q", self.primary_branch)
        result = self.scan(side_sha)
        self.assertNotEqual(0, result.returncode)

    def test_scanner_failure_propagates(self):
        result = self.scan(self.base, scanner_exit="1")
        self.assertEqual(1, result.returncode)

    def test_repository_allowlist_override_fails_closed(self):
        for filename in (".gitleaks.toml", ".gitleaksignore"):
            with self.subTest(filename=filename):
                override = self.repo / filename
                override.write_text("# untrusted scanner override\n")
                self.log.unlink(missing_ok=True)
                try:
                    result = self.scan(self.base)
                    self.assertNotEqual(0, result.returncode)
                    self.assertFalse(self.log.exists())
                finally:
                    override.unlink()

    def test_empty_range_fails_closed(self):
        result = self.scan(self.git("rev-parse", "HEAD").strip())
        self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
