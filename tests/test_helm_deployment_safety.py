from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CHART = ROOT / "deploy" / "helm" / "aetheris"


class HelmDeploymentSafetyTest(unittest.TestCase):
    def test_default_chart_requires_explicit_local_opt_in(self) -> None:
        values = (CHART / "values.yaml").read_text(encoding="utf-8")
        local_values = (CHART / "values-local.yaml").read_text(encoding="utf-8")

        self.assertIn("allowInsecureLocalDefaults: false", values)
        self.assertIn("allowInsecureLocalDefaults: true", local_values)
        self.assertIn("local learning/demo workflow only", local_values)

    def test_non_local_render_rejects_bundled_demo_credentials(self) -> None:
        gate = (CHART / "templates" / "00-safety-gate.yaml").read_text(encoding="utf-8")

        self.assertIn("if not .Values.global.allowInsecureLocalDefaults", gate)
        self.assertIn('eq .Values.secrets.jwtSecret "change-me-local-development-secret-32chars"', gate)
        self.assertIn('eq .Values.secrets.postgresPassword "aetheris"', gate)
        self.assertIn('eq .Values.secrets.rabbitmqPassword "aetheris"', gate)
        self.assertGreaterEqual(gate.count("fail "), 4)

    def test_non_local_render_requires_digest_pinned_images(self) -> None:
        gate = (CHART / "templates" / "00-safety-gate.yaml").read_text(encoding="utf-8")

        self.assertIn("range $name, $image := .Values.images", gate)
        self.assertIn('contains "@sha256:" $image', gate)
        self.assertIn("must be digest-pinned for non-local installs", gate)

    def test_runbook_requires_local_values_file_for_demo_commands(self) -> None:
        docs = (ROOT / "docs" / "kubernetes.md").read_text(encoding="utf-8")

        self.assertIn("values-local.yaml", docs)
        self.assertIn("explicit local-development opt-in", docs)
        self.assertIn("fails closed", docs)


if __name__ == "__main__":
    unittest.main()
