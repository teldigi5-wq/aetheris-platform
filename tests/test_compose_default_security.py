#!/usr/bin/env python3
"""Aetheris Compose security regression: fail closed, no wildcard host publication.

This checks real `docker compose config --format json` on a CI runner.
It does NOT start services or establish live network exposure / production safety.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMPOSES = ("docker-compose.yml", "docker-compose.core.yml",
            "docker-compose.integration-external.yml")
REQUIRED = ("AETHERIS_POSTGRES_PASSWORD", "AETHERIS_RABBITMQ_PASSWORD",
            "AETHERIS_JWT_SECRET", "AETHERIS_GRAFANA_ADMIN_PASSWORD")
SENSITIVE = {"postgres", "redis", "rabbitmq"}
HOST_BOUND = {"gateway", "dashboard", "user-service", "identity-service",
              "audit-service", "orchestrator-service", "prometheus", "tempo",
              "otel-collector", "loki", "alloy", "grafana"}


def run_config(filename: str, env: dict[str, str]):
    return subprocess.run(
        ["docker", "compose", "-f", filename, "config", "--format", "json"],
        cwd=ROOT, env=env, capture_output=True, text=True, timeout=60, check=False)


def clean_env():
    # Deliberately remove ANY secrets inherited from a developer/CI runner.
    env={k:v for k,v in os.environ.items() if k not in REQUIRED}
    env["AETHERIS_AI_RUNTIME_IMAGE"]="aetheris-ai-runtime:65a6262717adcd52ac8d8a16ed6f223e299fd74d"
    return env


def strong_env():
    env=clean_env()
    env.update({key:secrets.token_hex(32) for key in REQUIRED})
    return env


class DockerComposeSecurityBoundary(unittest.TestCase):
    def test_all_entrypoints_require_nonempty_config_secrets(self):
        env=clean_env()
        for filename in COMPOSES:
            with self.subTest(file=filename):
                result=run_config(filename,env)
                self.assertNotEqual(result.returncode,0,
                    "MISSING_SECRET_MUST_FAIL_COMPOSE_INTERPOLATION: "+filename)
                self.assertRegex(result.stderr+r"\n"+result.stdout,
                    "AETHERIS_(POSTGRES_PASSWORD|RABBITMQ_PASSWORD|JWT_SECRET|GRAFANA_ADMIN_PASSWORD)")
        for missing in REQUIRED[:3]:
            env=strong_env()
            env.pop(missing)
            result=run_config("docker-compose.yml",env)
            self.assertNotEqual(result.returncode,0,
                                "MISSING_"+missing+"_MUST_NOT_BOOT_DEFAULT")

    def test_port_bindings_never_public_or_expose_datastores(self):
        for filename in COMPOSES:
            with self.subTest(file=filename):
                result=run_config(filename,strong_env())
                self.assertEqual(result.returncode,0,
                    "DOCKER_COMPOSE_CONFIG_INVALID "+filename+": "+
                    result.stderr[-1500:])
                obj=json.loads(result.stdout)
                services=obj["services"]
                for datastore in SENSITIVE:
                    self.assertIn(datastore,services)
                    self.assertFalse(services[datastore].get("ports"),
                        "UNSAFE_PUBLISHED_INTERNAL_PORT "+filename+" "+datastore)
                for name,config in services.items():
                    self.assertIn(name,SENSITIVE|HOST_BOUND)
                    for binding in config.get("ports",[]):
                        self.assertEqual(binding.get("host_ip"),"127.0.0.1",
                            "UNSAFE_PUBLIC_BIND "+filename+" "+name+
                            " "+repr(binding))
                        self.assertNotIn(binding.get("published"),("5432","6379","5672","15672"))
                # Gateway/dashboard must remain usable for a local developer;
                # service-to-service traffic travels over the Compose network.
                self.assertTrue(services["gateway"].get("ports"))
                self.assertTrue(services["dashboard"].get("ports"))

    def test_no_committed_password_or_jwt_fallback(self):
        for filename in COMPOSES+("docker-compose.override.yml",):
            with self.subTest(file=filename):
                value=(ROOT/filename).read_text(encoding="utf-8")
                self.assertNotRegex(value,
                    r"(?:POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD|"
                    r"SPRING_RABBITMQ_PASSWORD|RABBITMQ_DEFAULT_PASS):\s*aetheris(?:\s|$)")
                self.assertNotIn("change-me-local-development-secret-32chars",value)
        for name in ("identity-service","user-service","audit-service","gateway"):
            text=(ROOT/name/"src/main/resources/application.yml").read_text("utf-8")
            self.assertNotIn("change-me-local-development-secret-32chars",text)
            self.assertNotIn("${SPRING_DATASOURCE_PASSWORD:aetheris}",text)
            self.assertNotIn("${SPRING_RABBITMQ_PASSWORD:aetheris}",text)
        self.assertIn(".env", (ROOT/".gitignore").read_text())
        example=(ROOT/".env.example").read_text()
        self.assertNotIn("change-me-local-development",example)
        for key in REQUIRED:
            self.assertRegex(example,r"(?m)^"+re.escape(key)+r"=$")

    def test_untrusted_override_does_not_republish_ports(self):
        env=strong_env()
        result=subprocess.run(["docker","compose","config","--format","json"],
            cwd=ROOT,env=env,capture_output=True,text=True,timeout=60,check=False)
        self.assertEqual(result.returncode,0,result.stderr[-1300:])
        services=json.loads(result.stdout)["services"]
        for name,svc in services.items():
            if name in SENSITIVE:
                self.assertFalse(svc.get("ports"),name)
            for port in svc.get("ports",[]):
                self.assertEqual(port.get("host_ip"),"127.0.0.1",name)


    def test_legacy_proof_overrides_share_database_password(self):
        # Prevent CI-only overrides from undoing hardened service credentials.
        overrides = (
            "connector-live-providers-phase3-compose.override.yml",
            "connector-provider-sync-phase4-compose.override.yml",
            "connector-live-write-phase7-compose.override.yml",
            "connector-live-write-phase8-compose.override.yml",
        )
        for name in overrides:
            with self.subTest(override=name):
                data = (ROOT/"build-evidence/runtime"/name).read_text("utf-8")
                self.assertIn("SPRING_DATASOURCE_PASSWORD: ${AETHERIS_POSTGRES_PASSWORD:?", data)
                self.assertNotIn("SPRING_DATASOURCE_PASSWORD: aetheris", data)
        boundary = (ROOT/"tools/core_deployment_boundary_smoke.py").read_text("utf-8")
        self.assertIn('os.environ.get("AETHERIS_POSTGRES_PASSWORD", "")', boundary)
        self.assertNotIn('f"SPRING_DATASOURCE_PASSWORD={RUNTIME_DATASOURCE_PASSWORD}"', boundary)

    def test_observability_has_no_docker_control_socket(self):
        for name in ("docker-compose.yml", "docker-compose.integration-external.yml"):
            content = (ROOT/name).read_text("utf-8")
            self.assertNotIn("/var/run/docker.sock", content)
            self.assertIn("./observability/logs:/var/log/aetheris:ro", content)
        alloy = (ROOT/"observability/alloy.alloy").read_text("utf-8")
        self.assertNotIn("/var/run/docker.sock", alloy)
        self.assertNotIn("discovery.docker", alloy)
        self.assertIn('loki.source.file "aetheris"', alloy)
        self.assertIn("/var/log/aetheris/gateway.log", alloy)
        self.assertIn("observability/logs/", (ROOT/".gitignore").read_text("utf-8"))


if __name__=="__main__":
    unittest.main()
