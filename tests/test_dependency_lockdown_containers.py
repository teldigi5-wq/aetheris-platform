from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "check_dependency_lockdown.py"
SPEC = importlib.util.spec_from_file_location("check_dependency_lockdown", MODULE_PATH)
lockdown = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(lockdown)


class CoreContainerPolicyTests(unittest.TestCase):
    def test_repository_core_dockerfiles_satisfy_container_policy(self) -> None:
        for relative in lockdown.CORE_DOCKERFILES:
            text = (ROOT / relative).read_text(encoding="utf-8")
            self.assertEqual([], lockdown.container_policy_errors(relative, text), relative)

    def test_mutable_base_image_is_rejected(self) -> None:
        digest = "a" * 64
        text = f"""FROM eclipse-temurin:21-jdk AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512={lockdown.MAVEN_3_9_11_SHA512}
FROM eclipse-temurin:21-jre@sha256:{digest}
USER 10001:10001
"""
        errors = lockdown.container_policy_errors("gateway/Dockerfile", text)
        self.assertTrue(any("mutable or invalid base image" in error for error in errors), errors)

    def test_missing_or_root_runtime_user_is_rejected(self) -> None:
        digest = "b" * 64
        prefix = f"""FROM eclipse-temurin:21-jdk@sha256:{digest} AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512={lockdown.MAVEN_3_9_11_SHA512}
FROM eclipse-temurin:21-jre@sha256:{digest}
"""
        missing = lockdown.container_policy_errors("identity-service/Dockerfile", prefix)
        self.assertTrue(any("no explicit non-root USER" in error for error in missing), missing)

        root = lockdown.container_policy_errors("identity-service/Dockerfile", prefix + "USER root\n")
        self.assertTrue(any("resolves to root USER" in error for error in root), root)

    def test_pinned_non_root_example_passes(self) -> None:
        digest = "c" * 64
        text = f"""FROM eclipse-temurin:21-jdk@sha256:{digest} AS build
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_SHA512={lockdown.MAVEN_3_9_11_SHA512}
FROM eclipse-temurin:21-jre@sha256:{digest}
USER 10001:10001
"""
        self.assertEqual([], lockdown.container_policy_errors("audit-service/Dockerfile", text))


class NodeSecurityPinConsistencyTests(unittest.TestCase):
    """Prevent partial Node security upgrades across CI, image and npm locks."""

    EXPECTED_NODE = "22.23.4"

    def test_node_version_matches_dashboard_and_first_boot_contract(self) -> None:
        import json
        version = (ROOT / ".nvmrc").read_text(encoding="utf-8").strip()
        self.assertEqual(self.EXPECTED_NODE, version)
        manifest = json.loads((ROOT / "dashboard/package.json").read_text("utf-8"))
        lock = json.loads((ROOT / "dashboard/package-lock.json").read_text("utf-8"))
        contract = json.loads((ROOT / "configs/first-boot-contract.json").read_text("utf-8"))
        self.assertEqual(version, manifest["engines"]["node"])
        self.assertEqual(version, lock["packages"][""]["engines"]["node"])
        self.assertEqual(version, contract["toolchain"]["node"])
        self.assertEqual(manifest["devDependencies"], lock["packages"][""]["devDependencies"])
        for dependency, patched_version in (("vite", "8.3.5"), ("source-map-js", "1.2.2")):
            entry = lock["packages"][f"node_modules/{dependency}"]
            self.assertEqual(patched_version, entry["version"])
            self.assertRegex(entry.get("integrity", ""), r"^sha(?:256|384|512)-")

    def test_ci_node_pins_and_container_image_are_consistent(self) -> None:
        import re
        version = self.EXPECTED_NODE
        checked = (
            ".github/workflows/build.yml",
            ".github/workflows/codeql.yml",
            ".github/workflows/stage24-foundation-bootstrap.yml",
            ".github/workflows/stage24-foundation-hardening.yml",
            "scripts/check_dependency_lockdown.py",
            "docs/first-boot-runbook.md",
        )
        for relative in checked:
            with self.subTest(path=relative):
                content = (ROOT / relative).read_text(encoding="utf-8")
                self.assertEqual({version}, set(re.findall(r"22\.23\.\d+", content)))
        dockerfile = (ROOT / "dashboard/Dockerfile").read_text(encoding="utf-8")
        self.assertRegex(
            dockerfile,
            rf"(?m)^FROM node:{re.escape(version)}-alpine3\.24@sha256:[0-9a-f]{{64}} AS build$",
        )


class DashboardNginxRuntimePinTests(unittest.TestCase):
    """Guard the final runtime stage independently of the Node build stage."""

    EXPECTED_NGINX_IMAGE = (
        "nginx:1.31.8-alpine3.24@sha256:"
        "713fc2ebfd92e8217db451477182ee91944b123d2b1ee9fd5a8c6e91ca6c3a11"
    )

    def test_dashboard_final_stage_has_reviewed_nginx_index_digest(self) -> None:
        import re

        dockerfile = (ROOT / "dashboard/Dockerfile").read_text(encoding="utf-8")
        stages = re.findall(r"(?m)^FROM\s+([^\r\n]+)$", dockerfile)
        self.assertEqual(2, len(stages), "dashboard must retain its two-stage build")
        self.assertEqual(self.EXPECTED_NGINX_IMAGE, stages[-1])
        self.assertIn("RUN apk add --no-cache 'zlib=1.4-r0'", dockerfile)
        self.assertIn("COPY nginx.conf /etc/nginx/conf.d/default.conf", dockerfile)
        self.assertIn("COPY --from=build /app/dist /usr/share/nginx/html", dockerfile)

if __name__ == "__main__":
    unittest.main()
