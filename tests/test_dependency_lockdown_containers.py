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


if __name__ == "__main__":
    unittest.main()
