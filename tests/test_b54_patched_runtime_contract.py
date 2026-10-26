"""B54 regression: patched Tomcat/RabbitMQ and AL2023 RPM runtime guard."""
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("gateway", "identity-service", "user-service", "audit-service")
WEBMVC = ("identity-service", "user-service", "audit-service")
AMQP = ("user-service", "audit-service")
M = {"m":"http://maven.apache.org/POM/4.0.0.0"}  # runtime XML namespace resolved below


def pom(service):
    return ElementTree.parse(ROOT / service / "pom.xml").getroot()


class B54PatchContract(unittest.TestCase):
    def test_tomcat_family_pins_for_all_three_webmvc_services(self):
        for svc in WEBMVC:
            with self.subTest(service=svc):
                root = pom(svc)
                ns = {"m": "http://maven.apache.org/POM/4.0.0"}
                self.assertEqual(
                    root.findtext("m:properties/m:tomcat.version", namespaces=ns),
                    "11.0.27",
                )

    def test_two_amqp_services_override_old_transitive_client_directly(self):
        for svc in AMQP:
            with self.subTest(service=svc):
                root = pom(svc)
                ns = {"m": "http://maven.apache.org/POM/4.0.0"}
                pairs = [
                    (d.findtext("m:groupId", namespaces=ns),
                     d.findtext("m:artifactId", namespaces=ns),
                     d.findtext("m:version", namespaces=ns))
                    for d in root.findall("m:dependencies/m:dependency", ns)
                ]
                self.assertIn(("com.rabbitmq", "amqp-client", "5.37.0"), pairs)

    def test_base_os_package_refresh_on_each_runtime_stage(self):
        for svc in SERVICES:
            dockerfile = (ROOT / svc / "Dockerfile").read_text()
            stages = dockerfile.split("\nFROM ")
            self.assertEqual(len(stages), 2)
            self.assertIn("RUN dnf -y upgrade && dnf -y install shadow-utils && dnf clean all", stages[1])
            self.assertIn("USER 10001:10001", stages[1])
            self.assertNotIn("USER root", stages[1])

    def test_security_build_source_pins_and_maven_archive_hash_preserved(self):
        for svc in SERVICES:
            dockerfile = (ROOT / svc / "Dockerfile").read_text()
            self.assertIn("amazoncorretto:21.0.13-al2023@sha256:", dockerfile)
            self.assertIn("amazoncorretto:21.0.13-al2023-headless@sha256:", dockerfile)
            self.assertIn("sha512sum --check --strict", dockerfile)

    def test_scanner_still_fails_on_serious_findings_and_retains_evidence(self):
        workflow = (ROOT / ".github/workflows/b53-actual-corretto-sbom-vulnerability.yml").read_text()
        self.assertIn("B53_RELEASE_NO_GO_SCANNER_FINDINGS_OR_UNVERIFIED_DATA", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("test_b54_patched_runtime_contract.py", workflow)


if __name__ == "__main__":
    unittest.main()
