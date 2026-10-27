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

    def test_emudhra_root_mitigation_on_both_stages(self) -> None:
        # Static regression gate only. Docker build executes real keytool proof.
        # Both build (Maven HTTPS) and runtime (application JSSE) must be hardened.
        for relative in lockdown.CORE_DOCKERFILES:
            dockerfile = (ROOT / relative).read_text(encoding="utf-8")
            stages = dockerfile.split("\nFROM ")
            self.assertEqual(2, len(stages), relative)
            for stage in stages:
                self.assertIn("CVE-2026-114820", stage, relative)
                self.assertIn("keytool -list -cacerts -storepass changeit >/dev/null", stage, relative)
                self.assertIn("keytool -delete -cacerts -storepass changeit", stage, relative)
                for alias in (
                    "emsignrootcag1 [jdk]",
                    "emsignrootcag2 [jdk]",
                    "emsigneccrootcag3 [jdk]",
                    "comodoaaaca [jdk]",
                ):
                    self.assertIn(alias, stage, relative)
                # Detect the two compromised-chain roots even if renamed in cacerts.
                self.assertIn("86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B", stage)
                self.assertIn("D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4", stage)

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


if __name__ == "__main__":
    unittest.main()
