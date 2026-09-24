import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage22" / "schema_guard.py"
SPEC = importlib.util.spec_from_file_location("stage22_schema_guard", MODULE_PATH)
guard = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(guard)


class Stage22SchemaGuardTests(unittest.TestCase):
    @staticmethod
    def _write(root: Path, relative: str, content: str) -> None:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_real_repository_scans_live_platform_services(self):
        report = guard.scan_repository(ROOT)
        self.assertEqual(report["status"], "PASS", report)
        self.assertIn("identity-service/src/main/java", report["javaRoots"])
        self.assertIn("user-service/src/main/java", report["javaRoots"])
        self.assertIn("identity-service/src/main/resources", report["resourceRoots"])
        self.assertIn("user-service/src/main/resources", report["resourceRoots"])
        self.assertFalse(any(path.startswith("orchestrator-service/") for path in report["javaRoots"]))
        self.assertFalse(any(path.startswith("orchestrator-service/") for path in report["resourceRoots"]))
        self.assertGreater(report["javaFileCount"], 0)
        self.assertGreater(report["resourceConfigFileCount"], 0)

    def test_fails_closed_when_no_live_main_roots_exist(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = guard.scan_repository(Path(tmp))
        self.assertEqual(report["status"], "FAIL")
        self.assertIn("no platform src/main/java roots discovered", report["scanErrors"])
        self.assertIn("no platform src/main/resources roots discovered", report["scanErrors"])

    def test_unsafe_main_resource_ddl_mode_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write(root, "sample-service/src/main/java/example/Entity.java", "class Entity {}\n")
            self._write(
                root,
                "sample-service/src/main/resources/application.yml",
                "spring:\n  jpa:\n    hibernate:\n      ddl-auto: update\n",
            )
            report = guard.scan_repository(root)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(
            report["unsafeProductionDdlModes"],
            [{"path": "sample-service/src/main/resources/application.yml", "mode": "update"}],
        )

    def test_test_resource_create_drop_is_outside_production_scan(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write(root, "sample-service/src/main/java/example/Entity.java", "class Entity {}\n")
            self._write(
                root,
                "sample-service/src/main/resources/application.yml",
                "spring:\n  jpa:\n    hibernate:\n      ddl-auto: validate\n",
            )
            self._write(
                root,
                "sample-service/src/test/resources/application-test.yml",
                "spring:\n  jpa:\n    hibernate:\n      ddl-auto: create-drop\n",
            )
            report = guard.scan_repository(root)
        self.assertEqual(report["status"], "PASS", report)
        self.assertEqual(report["unsafeProductionDdlModes"], [])

    def test_properties_style_unsafe_ddl_mode_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write(root, "sample-service/src/main/java/example/Entity.java", "class Entity {}\n")
            self._write(
                root,
                "sample-service/src/main/resources/application.properties",
                "spring.jpa.hibernate.ddl-auto=create-drop\n",
            )
            report = guard.scan_repository(root)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(report["unsafeProductionDdlModes"][0]["mode"], "create-drop")

    def test_duplicate_explicit_table_name_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._write(
                root,
                "service-a/src/main/java/example/First.java",
                '@Table(name = "shared_table")\nclass First {}\n',
            )
            self._write(root, "service-a/src/main/resources/application.yml", "spring:\n  application:\n    name: a\n")
            self._write(
                root,
                "service-b/src/main/java/example/Second.java",
                '@Table(name = "shared_table")\nclass Second {}\n',
            )
            self._write(root, "service-b/src/main/resources/application.yml", "spring:\n  application:\n    name: b\n")
            report = guard.scan_repository(root)
        self.assertEqual(report["status"], "FAIL")
        self.assertEqual(len(report["duplicateTableNames"]["shared_table"]), 2)


if __name__ == "__main__":
    unittest.main()
