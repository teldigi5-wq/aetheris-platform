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

    def test_helm_redis_authentication_is_required(self) -> None:
        values = (CHART / "values.yaml").read_text(encoding="utf-8")
        safety = (CHART / "templates" / "00-safety-gate.yaml").read_text(encoding="utf-8")
        core = (CHART / "templates" / "core.yaml").read_text(encoding="utf-8")
        docs = (ROOT / "docs" / "kubernetes.md").read_text(encoding="utf-8")
        gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        self.assertIn('redisPassword: ""', values)
        self.assertIn("lt (len (trim $redisSecret)) 32", safety)
        self.assertIn("must be independent of JWT, PostgreSQL and RabbitMQ", safety)
        self.assertIn("REDIS_PASSWORD: {{ .Values.secrets.redisPassword | quote }}", core)
        self.assertIn('key: REDIS_PASSWORD', core)
        self.assertIn("SPRING_DATA_REDIS_PASSWORD", core)
        self.assertIn('exec redis-server --appendonly yes --requirepass "$REDISCLI_AUTH"', core)
        self.assertIn("name: REDISCLI_AUTH", core)
        self.assertIn("values-local-private.yaml", docs)
        self.assertIn("deploy/helm/aetheris/values-local-private.yaml", gitignore)

    def test_non_local_ingress_is_restricted_to_named_same_namespace_clients(self) -> None:
        policy = (CHART / "templates" / "10-ingress-network-policies.yaml").read_text("utf-8")
        self.assertIn("if not .Values.global.allowInsecureLocalDefaults", policy)
        self.assertIn('policyTypes: ["Ingress"]', policy)
        self.assertNotIn("namespaceSelector:", policy)
        for name, port in (
            ("postgres", 5432), ("redis", 6379), ("rabbitmq", 5672),
            ("user-service", 8081), ("identity-service", 8082), ("audit-service", 8083)
        ):
            self.assertIn(f'"name" "{name}" "port" {port}', policy)
        self.assertIn('(list "user-service" "identity-service" "audit-service")', policy)
        self.assertIn('(list "user-service" "gateway")', policy)
        self.assertIn('(list "user-service" "audit-service")', policy)
        self.assertIn('"clients" (list "gateway")', policy)
        self.assertNotIn("0.0.0.0/0", policy)
        self.assertNotIn("ipBlock:", policy)

    def test_runbook_requires_local_values_file_for_demo_commands(self) -> None:
        docs = (ROOT / "docs" / "kubernetes.md").read_text(encoding="utf-8")

        self.assertIn("values-local.yaml", docs)
        self.assertIn("explicit local-development opt-in", docs)
        self.assertIn("fails closed", docs)


if __name__ == "__main__":
    unittest.main()
