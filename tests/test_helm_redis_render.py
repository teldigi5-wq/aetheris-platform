"""Verify real Helm rendering: no redis secret, no chart; valid secret wires server and clients."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CHART = ROOT / "deploy/helm/aetheris"


def render(*, with_secret: bool):
    if not shutil.which("helm"):
        raise RuntimeError("Helm CLI missing; CI must install it, never skip security render")
    with tempfile.TemporaryDirectory(prefix="aetheris-helm-security-") as tmp:
        secret_file = Path(tmp)/"private-values.yaml"
        secret_file.write_text(
            "secrets:\n  redisPassword: \"" + (secrets.token_hex(32) if with_secret else "") + "\"\n",
            encoding="utf-8",
        )
        os.chmod(secret_file, 0o600)
        return subprocess.run(
            ["helm","template","aetheris",str(CHART),
             "-f",str(CHART/"values-local.yaml"),"-f",str(secret_file)],
            capture_output=True,text=True,timeout=45,check=False
        )


class HelmRedisRuntimeRendering(unittest.TestCase):
    def test_local_demo_refuses_blank_redis_secret(self):
        r=render(with_secret=False)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("secrets.redisPassword must be at least 32 characters",r.stderr)

    def test_strong_private_secret_wires_server_and_both_clients(self):
        r=render(with_secret=True)
        self.assertEqual(r.returncode,0,r.stderr[-1300:])
        rendered=r.stdout
        self.assertIn("REDIS_PASSWORD:",rendered)
        self.assertIn('$(REDISCLI_AUTH)',rendered)
        self.assertIn("name: REDISCLI_AUTH",rendered)
        self.assertEqual(rendered.count("name: SPRING_DATA_REDIS_PASSWORD"),2)
        self.assertIn("key: REDIS_PASSWORD",rendered)
        self.assertIn("readinessProbe:",rendered)
        self.assertIn("livenessProbe:",rendered)


if __name__ == "__main__":
    unittest.main()
