"""Render the actual Helm chart locally with synthetic reference names only.

Requires Helm CLI on CI runner; never talks to Kubernetes or prints secrets.
"""
import pathlib
import re
import shutil
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
CHART = ROOT / "deploy/helm/aetheris"
DEMO = CHART / "values-local.yaml"
BASE = [
    "-f", str(DEMO),
    "--set-string", "secrets.redisPassword=synthetic-redis-test-secret-not-for-deploy-20261028",
]
RSA = [
    "--set", "authJwt.mode=rs256",
    "--set-string", "authJwt.issuer=https://identity.example.invalid",
    "--set-string", "authJwt.primaryAudience=aetheris-users",
    "--set-string", "authJwt.usersAudience=aetheris-users",
    "--set-string", "authJwt.auditAudience=aetheris-audit",
    "--set-string", "authJwt.orchestratorAudience=aetheris-orch",
    "--set-string", "authJwt.activeKeyId=kid-2026a",
    "--set-string", "authJwt.signerSecretName=identity-private-external",
    "--set-string", "authJwt.verifierSecretName=platform-public-external",
]

def helm(*flags, lint=False):
    if shutil.which("helm") is None:
        raise AssertionError("Helm CLI required for real template verification")
    cmd = ["helm", "lint", str(CHART)] if lint else ["helm", "template", "aetheris", str(CHART)]
    result = subprocess.run(cmd + BASE + list(flags), stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, text=True, timeout=40)
    return result

def deployments(render):
    parts = re.split(r"(?m)^---\s*$", render)
    result = {}
    for part in parts:
        if re.search(r"(?m)^kind: Deployment\s*$", part):
            m = re.search(r"(?m)^metadata:\s*\n  name: ([a-z0-9-]+)\s*$", part)
            if m:
                result[m.group(1)] = part
    return result

class HelmRs256ExternalKeysTest(unittest.TestCase):
    def test_legacy_render_preserves_hmac_and_all_four_authentication_services(self):
        rendered = helm()
        self.assertEqual(0, rendered.returncode, rendered.stderr)
        apps = deployments(rendered.stdout)
        for svc in ("identity-service", "gateway", "user-service", "audit-service"):
            self.assertIn(svc, apps)
            self.assertIn("- name: AETHERIS_JWT_SECRET", apps[svc])
            self.assertNotIn("JWT_RS256_PRIVATE_KEY_PEM", apps[svc])
        self.assertIn("AETHERIS_JWT_SECRET:", rendered.stdout)
        self.assertEqual(0, helm(lint=True).returncode)

    def test_rsa_only_renders_external_secret_references_and_no_hmac_secret(self):
        render = helm(*RSA)
        self.assertEqual(0, render.returncode, render.stderr)
        docs = deployments(render.stdout)
        for svc in ("identity-service", "gateway", "user-service", "audit-service"):
            self.assertIn(svc, docs)
            self.assertNotIn("AETHERIS_JWT_SECRET", docs[svc])
        self.assertNotIn("AETHERIS_JWT_SECRET:", render.stdout)
        signer = docs["identity-service"]
        self.assertIn("- name: AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM", signer)
        self.assertIn('name: "identity-private-external"', signer)
        self.assertIn("key: AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM", signer)
        for svc in ("user-service", "audit-service", "gateway"):
            self.assertNotIn("AETHERIS_IDENTITY_JWT_RS256_PRIVATE_KEY_PEM", docs[svc])
            self.assertIn("platform-public-external", docs[svc])
            self.assertIn("key: AETHERIS_JWT_RS256_PUBLIC_KEY_PEM", docs[svc])
            self.assertIn("kid-2026a", docs[svc])
        self.assertNotIn("-----BEGIN PRIVATE KEY-----", render.stdout)
        self.assertIn('value: "aetheris-audit"', docs["audit-service"])
        self.assertIn('value: "aetheris-users"', docs["user-service"])
        self.assertEqual(0, helm(*RSA, lint=True).returncode)

    def test_staged_rotation_only_adds_public_key_refs(self):
        stage = ["--set-string", "authJwt.stagedKeyId=kid-2026b"]
        render = helm(*RSA, *stage)
        self.assertEqual(0, render.returncode, render.stderr)
        docs = deployments(render.stdout)
        for svc in ("user-service", "audit-service", "gateway"):
            self.assertIn("key: AETHERIS_JWT_RS256_STAGED_PUBLIC_KEY_PEM", docs[svc])
            self.assertIn("kid-2026b", docs[svc])
        self.assertNotIn("STAGED_PUBLIC_KEY_PEM", docs["identity-service"])

    def test_misconfigured_helm_values_fail_render(self):
        for args in (
            ["--set", "authJwt.mode=typo"],
            ["--set-string", "authJwt.issuer=https://mixed.invalid"],
        ):
            with self.subTest(legacy=args):
                out = helm(*args)
                self.assertNotEqual(0, out.returncode)
        for option in (
            ["--set-string", "authJwt.issuer="],
            ["--set-string", "authJwt.primaryAudience=wrong"],
            ["--set-string", "authJwt.usersAudience=aetheris-audit"],
            ["--set-string", "authJwt.activeKeyId=../wrong"],
            ["--set-string", "authJwt.signerSecretName=platform-public-external"],
            ["--set-string", "authJwt.verifierSecretName="],
            ["--set-string", "authJwt.stagedKeyId=kid-2026a"],
        ):
            with self.subTest(rsa=option):
                self.assertNotEqual(0, helm(*RSA, *option).returncode)

if __name__ == "__main__":
    unittest.main()
