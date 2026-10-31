"""Real Helm render rejection tests with disposable, never-printed credentials.

Tests work without Docker, a Kubernetes cluster, or production secrets.
The workflow must provide Helm. No rendered Secrets are uploaded or logged.
"""
from __future__ import annotations

import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import unittest

CHART = Path(__file__).resolve().parents[1] / "deploy" / "helm" / "aetheris"
IMAGE_NAMES = (
    "gateway", "userService", "identityService", "auditService",
    "dashboard", "postgres", "redis", "rabbitmq",
)


class NonlocalHelmCredentialBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="aetheris-helm-secret-proof-")
        self.addCleanup(self.temp.cleanup)
        self.credential_values = {
            "jwtSecret": secrets.token_hex(32),
            "postgresPassword": secrets.token_hex(32),
            "rabbitmqPassword": secrets.token_hex(32),
            "redisPassword": secrets.token_hex(32),
            "rabbitmqUsername": "synthetic-user",
        }

    def render(self, overrides=None):
        values = dict(self.credential_values)
        values.update(overrides or {})
        # JSON string scalars are valid YAML. Avoid external yaml dependencies.
        parts = ["global:", "  allowInsecureLocalDefaults: false", "secrets:"]
        parts += [f"  {key}: {json.dumps(value)}" for key, value in values.items()]
        parts.append("images:")
        parts += [
            f"  {name}: {json.dumps('registry.invalid/aetheris-' + name + '@sha256:' + '0' * 64)}"
            for name in IMAGE_NAMES
        ]
        path = Path(self.temp.name) / "synthetic-private-values.yaml"
        path.write_text("\n".join(parts) + "\n", encoding="utf-8")
        path.chmod(0o600)
        try:
            return subprocess.run(
                ["helm", "template", "aetheris", str(CHART),
                 "--namespace", "aetheris", "-f", str(path)],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                text=True, check=False, timeout=30,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            self.fail(f"Required Helm renderer unavailable or timed out: {type(error).__name__}")

    def assert_rejected(self, overrides, expected_fragment):
        result = self.render(overrides)
        # Never show rendered Secret data or full stderr, even on failure.
        self.assertNotEqual(result.returncode, 0, "Unsafe synthetic credentials unexpectedly rendered")
        self.assertIn(
            expected_fragment,
            result.stderr,
            "Helm rejected input for the wrong reason; stderr withheld to protect secrets",
        )

    def test_independent_strong_nonlocal_credentials_render(self):
        result = self.render()
        self.assertEqual(
            0, result.returncode,
            "Strong synthetic non-local credentials were rejected; stderr withheld",
        )
        self.assertIn("kind: Secret", result.stdout)
        self.assertIn("kind: NetworkPolicy", result.stdout)

    def test_empty_and_short_credentials_are_denied(self):
        for name in ("jwtSecret", "postgresPassword", "rabbitmqPassword"):
            for value in ("", "too-short", "x" * 31, " " * 40):
                with self.subTest(name=name, length=len(value)):
                    self.assert_rejected({name: value}, "requires 32+ non-whitespace characters")

    def test_no_nonlocal_password_reuse(self):
        pairs = (
            ("jwtSecret", "postgresPassword"),
            ("jwtSecret", "rabbitmqPassword"),
            ("postgresPassword", "rabbitmqPassword"),
        )
        for left, right in pairs:
            with self.subTest(pair=f"{left}/{right}"):
                self.assert_rejected(
                    {right: self.credential_values[left]},
                    "must all be independent",
                )

    def test_nonlocal_redis_must_remain_independent(self):
        for reuse in ("jwtSecret", "postgresPassword", "rabbitmqPassword"):
            with self.subTest(reuse=reuse):
                self.assert_rejected(
                    {"redisPassword": self.credential_values[reuse]},
                    "must be independent of JWT",
                )

    def test_nonlocal_rabbitmq_username_cannot_be_empty(self):
        for name in ("", " ", " \t "):
            with self.subTest(length=len(name)):
                self.assert_rejected(
                    {"rabbitmqUsername": name},
                    "nonempty explicit username is required",
                )

    def test_known_demo_credentials_are_not_nonlocal_accepted(self):
        candidates = {
            "jwtSecret": "change-me-local-development-secret-32chars",
            "postgresPassword": "aetheris",
            "rabbitmqPassword": "aetheris",
        }
        for name, value in candidates.items():
            with self.subTest(name=name):
                result = self.render({name: value})
                self.assertNotEqual(
                    0, result.returncode,
                    "Known local demo credential was accepted in non-local mode",
                )


if __name__ == "__main__":
    unittest.main()
