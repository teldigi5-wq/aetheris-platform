from pathlib import Path
from xml.etree import ElementTree
import unittest

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class Spring4OtlpTracingContract(unittest.TestCase):
    def test_all_core_services_use_boot4_opentelemetry_starter_and_endpoint(self):
        for module in ("gateway", "identity-service", "user-service", "audit-service"):
            with self.subTest(module=module):
                root = ElementTree.parse(ROOT / module / "pom.xml").getroot()
                artifacts = [item.text for item in root.findall(
                    "m:dependencies/m:dependency/m:artifactId", NS
                )]
                self.assertIn("spring-boot-starter-opentelemetry", artifacts)
                self.assertNotIn("opentelemetry-exporter-otlp", artifacts)
                self.assertNotIn("micrometer-tracing-bridge-otel", artifacts)
                config = (ROOT / module / "src/main/resources/application.yml").read_text()
                self.assertIn("  opentelemetry:\n    tracing:\n      export:\n        otlp:\n          endpoint:", config)
                self.assertIn(
                    "${MANAGEMENT_OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}",
                    config,
                )
                self.assertNotIn("  otlp:\n    tracing:\n      endpoint:", config)


if __name__ == "__main__":
    unittest.main()
