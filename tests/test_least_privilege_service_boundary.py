"""I4 least-privilege and service-boundary regressions (B62/S0 integration, R8/R9/R16/R17).

- R8: Identity issues non-admin roles only the scopes they can use. User and Audit
  APIs are ADMIN-only at the gateway and in each service, so API_CONSUMER and
  DEVELOPER carry no users:* or services:* scopes.
- R9: user, audit and identity services expose only health,info by default. Metrics
  are an explicit opt-in (AETHERIS_SERVICE_ACTUATOR_EXPOSURE) used by the local
  observability proof, never a default.
- R16: no Compose service or Alloy config mounts or discovers the Docker socket.
- R17: the first-boot contract never expects PostgreSQL, Redis or RabbitMQ host ports.
"""
from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICES = ("user-service", "audit-service", "identity-service")
COMPOSE_FILES = ("docker-compose.yml", "docker-compose.core.yml", "docker-compose.integration-external.yml")


def role_scopes() -> dict[str, set[str]]:
    text = (ROOT / "identity-service/src/main/java/io/aetheris/identity/Role.java").read_text(encoding="utf-8")
    roles = {}
    for name, body in re.findall(r"(?m)^\s+([A-Z_]+)\(Set\.of\(([^)]*)\)\)", text):
        roles[name] = set(re.findall(r'"([a-z]+:[a-z]+)"', body))
    return roles


def compose_service_block(text: str, service: str) -> str:
    match = re.search(rf"(?m)^  {re.escape(service)}:\s*$([\s\S]*?)(?=^  [a-z][\w-]*:\s*$|^[a-z]|\Z)", text)
    if match is None:
        raise AssertionError(f"service {service} not found")
    return match.group(1)


class LeastPrivilegeRoleScopes(unittest.TestCase):
    def test_non_admin_roles_have_no_user_or_audit_scopes(self) -> None:
        roles = role_scopes()
        self.assertEqual({"ADMIN", "DEVELOPER", "API_CONSUMER"}, set(roles))
        self.assertEqual({"orchestrator:read"}, roles["API_CONSUMER"])
        for role in ("DEVELOPER", "API_CONSUMER"):
            with self.subTest(role=role):
                self.assertFalse({s for s in roles[role] if s.startswith(("users:", "services:"))})

    def test_admin_keeps_administrative_scopes(self) -> None:
        admin = role_scopes()["ADMIN"]
        self.assertTrue({"users:read", "users:write", "services:read"} <= admin)

    def test_self_registration_still_defaults_to_least_privileged_role(self) -> None:
        account = (ROOT / "identity-service/src/main/java/io/aetheris/identity/IdentityAccount.java").read_text(encoding="utf-8")
        self.assertIn("private Role role = Role.API_CONSUMER;", account)


class ServiceActuatorDefaultDeny(unittest.TestCase):
    def test_services_expose_only_health_and_info_by_default(self) -> None:
        for service in SERVICES:
            with self.subTest(service=service):
                yml = (ROOT / service / "src/main/resources/application.yml").read_text(encoding="utf-8")
                includes = re.findall(r"(?m)^\s+include:\s*(\S+)\s*$", yml)
                self.assertEqual(["${AETHERIS_SERVICE_ACTUATOR_EXPOSURE:health,info}"], includes)

    def test_compose_defaults_keep_metrics_closed(self) -> None:
        for name in COMPOSE_FILES:
            text = (ROOT / name).read_text(encoding="utf-8")
            for service in SERVICES:
                with self.subTest(compose=name, service=service):
                    block = compose_service_block(text, service)
                    self.assertIn("AETHERIS_SERVICE_ACTUATOR_EXPOSURE: ${AETHERIS_SERVICE_ACTUATOR_EXPOSURE:-health,info}", block)
                    self.assertNotIn("prometheus", block)

    def test_only_the_observability_proof_opts_in_to_service_metrics(self) -> None:
        workflows = ROOT / ".github/workflows"
        opted_in = sorted(p.name for p in workflows.glob("*.yml")
                          if "AETHERIS_SERVICE_ACTUATOR_EXPOSURE:" in p.read_text(encoding="utf-8"))
        self.assertEqual(["observability-runtime-proof.yml"], opted_in)


class HostBoundaryRegressions(unittest.TestCase):
    def test_no_docker_socket_mount_or_discovery(self) -> None:
        candidates = [ROOT / name for name in COMPOSE_FILES] + sorted((ROOT / "observability").glob("*"))
        for path in candidates:
            if path.is_file() and path.suffix != ".md":
                with self.subTest(path=path.name):
                    self.assertNotIn("docker.sock", path.read_text(encoding="utf-8"))

    def test_first_boot_contract_never_expects_infrastructure_host_ports(self) -> None:
        contract = json.loads((ROOT / "configs/first-boot-contract.json").read_text(encoding="utf-8"))
        ports = set(contract["expected_platform_ports"])
        self.assertFalse(ports & {5432, 5672, 6379, 15672})


if __name__ == "__main__":
    unittest.main()
