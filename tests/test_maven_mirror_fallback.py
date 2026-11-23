"""Java image builds: GCS copy of Maven Central first, Central kept as fallback.

Guards the 2026-11-23 rate-limit fix: no build may make the mirror the only source,
and every build must verify checksums strictly.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
DOCKERFILES = ("gateway/Dockerfile", "identity-service/Dockerfile", "user-service/Dockerfile",
               "audit-service/Dockerfile", "tests/security/b62g4-Dockerfile")
GCS = "https://maven-central.storage-download.googleapis.com/maven2/"


class MavenMirrorFallbackTest(unittest.TestCase):
    def test_gcs_repository_is_additional_never_a_mirror_of_central(self) -> None:
        for name in DOCKERFILES:
            with self.subTest(dockerfile=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                self.assertIn("<repositories><repository><id>gcs-maven-central</id>", text)
                self.assertIn("<pluginRepositories><pluginRepository><id>gcs-maven-central</id>", text)
                self.assertIn(GCS, text)
                self.assertNotIn("<mirrorOf>", text, "a mirrorOf would remove the Central fallback")

    def test_checksums_are_strict_and_tarball_hash_is_pinned(self) -> None:
        for name in DOCKERFILES:
            with self.subTest(dockerfile=name):
                text = (ROOT / name).read_text(encoding="utf-8")
                self.assertIn('ENV MAVEN_ARGS="--strict-checksums ', text)
                self.assertIn('sha512sum --check --strict', text)
                self.assertLess(text.index(GCS + "org/apache/maven/apache-maven/"),
                                text.index("https://repo.maven.apache.org/maven2/org/apache/maven/"))


if __name__ == "__main__":
    unittest.main()
