#!/usr/bin/env python3
"""Validate real rendered Compose configuration without leaking secrets.

This is a review contract, not production cluster penetration testing.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import re
import subprocess

FILENAMES = (
    ("docker-compose.yml", "docker-compose.override.yml"),
    ("docker-compose.core.yml",),
    ("docker-compose.integration-external.yml",),
)
REQUIRED = ("AETHERIS_POSTGRES_PASSWORD", "AETHERIS_RABBIT_PASSWORD",
            "AETHERIS_JWT_SECRET", "AETHERIS_GRAFANA_PASSWORD")
SENSITIVE = ("postgres", "redis", "rabbitmq", "user-service", "identity-service",
             "audit-service", "gateway", "orchestrator-service", "dashboard", "grafana",
             "prometheus", "tempo", "otel-collector", "loki", "alloy")
STATIC_SECRET = re.compile(r"(?m)^\s*(?:POSTGRES_PASSWORD|SPRING_DATASOURCE_PASSWORD|"
    r"RABBITMQ_DEFAULT_PASS|SPRING_RABBITMQ_PASSWORD|GF_SECURITY_ADMIN_PASSWORD):\s*aetheris\s*$")
WILDCARD = re.compile(r'(?m)^\s*- "(?!127\.0\.0\.1:)\d+:\d+"\s*$')

def rendered(paths, environment):
    cmd = ["docker", "compose"]
    for path in paths:
        cmd.extend(["-f", path])
    cmd.extend(["config", "--format", "json"])
    completed = subprocess.run(cmd, env=environment, capture_output=True,
                               text=True, timeout=25, check=False)
    if completed.returncode:
        # Never print docker compose stderr, which can interpolate secrets.
        raise ValueError("Compose configuration invalid")
    cfg = json.loads(completed.stdout)
    if not isinstance(cfg, dict) or not isinstance(cfg.get("services"), dict):
        raise ValueError("Malformed Compose rendering")
    return cfg

def verify():
    for key in REQUIRED:
        if not os.environ.get(key):
            raise ValueError("Missing mandatory synthetic test variable")
    for paths in FILENAMES:
        for path in paths:
            content = Path(path).read_text(encoding="utf-8")
            if STATIC_SECRET.search(content) or WILDCARD.search(content):
                raise ValueError("Unsafe static credential or public TCP binding in source")
        result = rendered(paths, os.environ.copy())
        services = result["services"]
        for name, service in services.items():
            if name not in SENSITIVE:
                continue
            for port in service.get("ports", []):
                if not isinstance(port, dict) or port.get("host_ip") != "127.0.0.1":
                    raise ValueError("Non-loopback published port: " + name)
        postgres = services.get("postgres", {}).get("environment", {})
        rabbitmq = services.get("rabbitmq", {}).get("environment", {})
        if postgres.get("POSTGRES_PASSWORD") != os.environ["AETHERIS_POSTGRES_PASSWORD"]:
            raise ValueError("Postgres password mismatch")
        if rabbitmq.get("RABBITMQ_DEFAULT_PASS") != os.environ["AETHERIS_RABBIT_PASSWORD"]:
            raise ValueError("RabbitMQ password mismatch")
        for name in ("user-service", "identity-service", "audit-service", "orchestrator-service"):
            if name not in services:
                continue
            env = services[name].get("environment", {})
            if env.get("SPRING_DATASOURCE_PASSWORD") != os.environ["AETHERIS_POSTGRES_PASSWORD"]:
                raise ValueError("Service database secret not wired: " + name)
            if name in ("user-service", "audit-service"):
                if env.get("SPRING_RABBITMQ_PASSWORD") != os.environ["AETHERIS_RABBIT_PASSWORD"]:
                    raise ValueError("Service RabbitMQ credential missing: " + name)
        for name in ("user-service", "identity-service"):
            if name in services and services[name].get("environment", {}).get(
                    "SPRING_JPA_HIBERNATE_DDL_AUTO") == "update":
                raise ValueError("Unsafe automatic persistent schema changes: " + name)
        if "identity-service" in services:
            if services["identity-service"].get("environment", {}).get(
                    "AETHERIS_BROWSER_REFRESH_COOKIE_SECURE") != "true":
                raise ValueError("Insecure refresh cookie compose default")
        print("XERJ_COMPOSE_RENDERED_BOUNDARIES_PASS files=" + ",".join(paths) +
              " services=" + str(len(services)))
    # Each critical externally supplied credential must be mandatory on
    # the full stack. Ensure the compose parser itself rejects its absence.
    for absent in ("AETHERIS_POSTGRES_PASSWORD", "AETHERIS_RABBIT_PASSWORD",
                   "AETHERIS_JWT_SECRET", "AETHERIS_GRAFANA_PASSWORD"):
        env = os.environ.copy()
        env.pop(absent, None)
        command = ["docker", "compose", "-f", "docker-compose.yml", "-f",
                   "docker-compose.override.yml", "config", "--quiet"]
        check = subprocess.run(command, env=env, capture_output=True, text=True,
                               timeout=25, check=False)
        if check.returncode == 0:
            raise ValueError("Root Compose accepted missing required: " + absent)
    print("XERJ_COMPOSE_FAIL_CLOSED_MISSING_SECRET_PASS count=4")

if __name__ == "__main__":
    try:
        verify()
    except (ValueError, OSError, json.JSONDecodeError, subprocess.TimeoutExpired) as ex:
        print("XERJ_COMPOSE_SECURITY_HOLD reason=" + type(ex).__name__)
        raise SystemExit(1)
