"""Release gate for Docker Compose host binding boundaries.

Tests rendered Compose JSON rather than trusting YAML string substitutions.
This is hosted CI evidence only; it does not inspect deployed firewalls.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILES = (
    "docker-compose.yml",
    "docker-compose.core.yml",
    "docker-compose.integration-external.yml",
)
PRIVATE = {"postgres", "redis", "rabbitmq"}
HOST_IP = "127.0.0.1"


def render_compose(*filenames: str) -> dict:
    env = dict(os.environ)
    # Test-only public fixture. Never load developer or production secrets.
    env["AETHERIS_AI_RUNTIME_IMAGE"] = "aetheris-ai-runtime:65a6262717adcd52ac8d8a16ed6f223e299fd74d"
    cmd = ["docker", "compose"]
    for name in filenames:
        cmd.extend(["-f", name])
    cmd.extend(["config", "--format", "json"])
    result = subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True,
                            text=True, timeout=50, check=False)
    if result.returncode:
        raise AssertionError(f"Compose config failed for {filenames}: {result.stderr[-1200:]}")
    return json.loads(result.stdout)


class ComposeNetworkBoundaryTests(unittest.TestCase):
    def assert_safe_ports(self, services: dict, context: str) -> None:
        for name, service in services.items():
            ports = service.get("ports", [])
            if name in PRIVATE:
                self.assertEqual([], ports,
                                 f"{context}: private {name} must not publish any host port")
            for port in ports:
                self.assertEqual(HOST_IP, port.get("host_ip"),
                                 f"{context}: {name} has a non-loopback host port: {port!r}")
                self.assertNotIn(str(port.get("published")), {"5432", "6379", "5672", "15672"},
                                 f"{context}: published sensitive port on {name}")

    def test_each_entrypoint_binds_only_loopback(self) -> None:
        for compose in COMPOSE_FILES:
            with self.subTest(compose=compose):
                services = render_compose(compose)["services"]
                self.assert_safe_ports(services, compose)
                for name in ("gateway", "dashboard"):
                    self.assertTrue(services[name].get("ports"), f"{name} must be usable locally")

    def test_default_compose_override_cannot_republish_stores(self) -> None:
        obj = render_compose("docker-compose.yml", "docker-compose.override.yml")
        self.assert_safe_ports(obj["services"], "default compose plus override")


if __name__ == "__main__":
    unittest.main()
