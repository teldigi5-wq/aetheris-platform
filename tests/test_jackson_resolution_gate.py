"""B50: dependency-policy negative tests, no network or Maven required."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from scripts.verify_jackson_resolved import MODULES, check_pom, verify

ROOT = Path(__file__).resolve().parents[1]
VALID = """io.aetheris:user-service:jar:0.1.0-SNAPSHOT
+- org.springdoc:springdoc-openapi-starter-webmvc-ui:jar:3.1.1:compile
|  +- com.fasterxml.jackson.core:jackson-databind:jar:2.21.8:compile
|  |  \\- com.fasterxml.jackson.core:jackson-core:jar:2.21.8:compile
|  +- com.fasterxml.jackson.core:jackson-annotations:jar:2.21:compile
\\- tools.jackson.core:jackson-databind:jar:3.1.8:compile
"""


class JacksonResolvedGateTests(unittest.TestCase):
    def test_correct_user_dual_family(self):
        self.assertEqual(verify("user-service", VALID), 4)

    def test_vulnerable_jackson_2_databind_denied(self):
        for version in ("2.21.5", "2.21.6", "2.21.7"):
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    verify("user-service", VALID.replace("jackson-databind:jar:2.21.8",
                                                         "jackson-databind:jar:" + version))

    def test_old_jackson3_denied(self):
        for version in ("3.1.5", "3.1.6", "3.1.7"):
            with self.subTest(version=version):
                with self.assertRaises(ValueError):
                    verify("user-service", VALID.replace("jackson-databind:jar:3.1.8",
                                                         "jackson-databind:jar:" + version))

    def test_sibling_jackson_core_drift_rejected(self):
        with self.assertRaises(ValueError):
            verify("user-service", VALID.replace("jackson-core:jar:2.21.8",
                                                 "jackson-core:jar:2.21.5"))
        with self.assertRaises(ValueError):
            verify("user-service", VALID.replace("jackson-annotations:jar:2.21",
                                                 "jackson-annotations:jar:2.21.8"))

    def test_unrecognized_maven_tree_format_not_ignored(self):
        with self.assertRaises(ValueError):
            verify("user-service", VALID + "com.fasterxml.jackson.core:jackson-databind:UNKNOWN-FORMAT\n")
        with self.assertRaises(ValueError):
            verify("user-service", "")

    def test_missing_legacy_databind_cannot_be_hidden_by_jackson3(self):
        with self.assertRaises(ValueError):
            verify("user-service",
                   "io.aetheris:user-service:jar:0.1.0-SNAPSHOT\n"
                   "\\- tools.jackson.core:jackson-databind:jar:3.1.8:compile\n")

    def test_unlisted_module_rejected(self):
        with self.assertRaises(ValueError):
            verify("admin", VALID)

    def test_all_four_current_poms_use_explicit_aligned_boms(self):
        self.assertEqual(len(MODULES), 4)
        for module in MODULES:
            with self.subTest(module=module):
                check_pom(ROOT / module / "pom.xml")

    def test_real_checkin_maven_tree_is_patched_but_not_ci_authority(self):
        tree = (ROOT / "build-evidence/maven/user-service.txt").read_text()
        self.assertGreaterEqual(verify("user-service", tree), 4)

    def test_valid_cli_returns_pass_only_with_actual_tree_argument(self):
        with tempfile.TemporaryDirectory() as t:
            tree = Path(t) / "tree.txt"
            tree.write_text(VALID)
            ok = subprocess.run(
                [sys.executable, "-m", "scripts.verify_jackson_resolved",
                 "user-service", str(tree)], cwd=ROOT, text=True,
                capture_output=True, check=False
            )
            self.assertEqual(ok.returncode, 0)
            self.assertEqual(ok.stdout.strip(), "JACKSON_RESOLUTION_PASS_USER_SERVICE_4")
            tree.write_text(VALID.replace("jackson-core:jar:2.21.8",
                                          "jackson-core:jar:2.21.5"))
            bad = subprocess.run(
                [sys.executable, "-m", "scripts.verify_jackson_resolved",
                 "user-service", str(tree)], cwd=ROOT, text=True,
                capture_output=True, check=False
            )
            self.assertEqual(bad.returncode, 1)
            self.assertEqual(bad.stdout.strip(), "JACKSON_RESOLUTION_NO_GO")


if __name__ == "__main__":
    unittest.main()
