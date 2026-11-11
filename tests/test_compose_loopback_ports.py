"""Compose port-exposure boundary.

Every Docker Compose file in this repository must publish host ports on the
loopback interface only. Docker's default ("8081:8081") binds 0.0.0.0, which
exposes services that trust the gateway (user, audit, identity, orchestrator)
and infrastructure with demo credentials (Postgres, Redis, RabbitMQ, Grafana)
to anyone who can reach the host, and bypasses host firewalls such as ufw.

Anyone who needs LAN access must add an explicit, local-only override file;
that choice must never be the repository default.
"""

from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
LOOPBACK = {"127.0.0.1", "[::1]", "::1"}

# "- 8080:8080", '- "8080:8080"', "- 127.0.0.1:8080:8080", "- 9000-9001:9000-9001/tcp"
SHORT_PORT = re.compile(r'^\s*-\s*["\']?(?P<spec>[0-9A-Za-z.\[\]:${}_\-]+:[0-9]+(?:-[0-9]+)?(?:/(?:tcp|udp))?)["\']?\s*(?:#.*)?$')
LONG_PUBLISHED = re.compile(r"^\s*-?\s*published\s*:")
LONG_HOST_IP = re.compile(r"^\s*host_ip\s*:\s*[\"']?(?P<ip>[^\"'\s#]+)")


def compose_files() -> list[Path]:
    tracked = subprocess.run(
        ["git", "ls-files"], cwd=ROOT, check=True, capture_output=True, text=True
    ).stdout.splitlines()
    return [ROOT / p for p in tracked if re.search(r"compose[^/]*\.ya?ml$", p)]


def host_ip(spec: str) -> str | None:
    """Return the host IP of a short-syntax mapping, or None if it has none."""
    # Strip the container port, then the host port; what remains (if anything) is the IP.
    parts = spec.split("/")[0]
    if parts.startswith("["):  # IPv6 literal, e.g. [::1]:8080:8080
        return parts[: parts.index("]") + 1]
    pieces = parts.split(":")
    return ":".join(pieces[:-2]) if len(pieces) > 2 else None


def exposures(path: Path) -> list[str]:
    problems = []
    lines = path.read_text(encoding="utf-8").splitlines()
    in_ports = False
    ports_indent = 0
    for number, line in enumerate(lines, start=1):
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        if re.match(r"^ports\s*:\s*$", stripped):
            in_ports, ports_indent = True, indent
            continue
        if in_ports and stripped and indent <= ports_indent and not stripped.startswith("-"):
            in_ports = False
        if not in_ports or not stripped or stripped.startswith("#"):
            continue
        match = SHORT_PORT.match(line)
        if match:
            ip = host_ip(match.group("spec"))
            if ip not in LOOPBACK:
                problems.append(f"{path.relative_to(ROOT)}:{number}: {stripped}")
            continue
        if LONG_PUBLISHED.match(line):
            # Long syntax: require a loopback host_ip somewhere in the same entry.
            entry = lines[number - 1 : number + 5]
            ips = [m.group("ip") for l in entry if (m := LONG_HOST_IP.match(l))]
            if not ips or ips[0] not in LOOPBACK:
                problems.append(f"{path.relative_to(ROOT)}:{number}: long-syntax port without loopback host_ip")
    return problems


class ComposeLoopbackPortsTest(unittest.TestCase):
    def test_compose_files_are_discovered(self) -> None:
        names = {p.name for p in compose_files()}
        self.assertIn("docker-compose.yml", names)
        self.assertIn("docker-compose.core.yml", names)

    def test_every_published_port_is_loopback_only(self) -> None:
        problems = [p for f in compose_files() for p in exposures(f)]
        self.assertEqual([], problems, "ports published beyond loopback:\n" + "\n".join(problems))

    def test_internal_services_are_still_published_for_local_ci_probes(self) -> None:
        # The runtime-proof workflows call these on localhost; keep them, loopback-only.
        compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
        for port in ("8080", "8081", "8082", "8083"):
            self.assertIn(f'"127.0.0.1:{port}:{port}"', compose)


class ExposureDetectorNegativeTest(unittest.TestCase):
    """The detector itself must flag the unsafe forms."""

    def scan(self, text: str) -> list[str]:
        path = ROOT / "build" / "tmp-compose-probe.yml"
        path.parent.mkdir(exist_ok=True)
        path.write_text(text, encoding="utf-8")
        try:
            return exposures(path)
        finally:
            path.unlink()

    def test_flags_default_all_interface_binding(self) -> None:
        self.assertEqual(1, len(self.scan('services:\n  a:\n    ports:\n      - "8081:8081"\n')))
        self.assertEqual(1, len(self.scan("services:\n  a:\n    ports:\n      - 5432:5432\n")))

    def test_flags_explicit_wildcard_and_variable_hosts(self) -> None:
        self.assertEqual(1, len(self.scan('services:\n  a:\n    ports:\n      - "0.0.0.0:8080:8080"\n')))
        self.assertEqual(1, len(self.scan('services:\n  a:\n    ports:\n      - "${BIND}:8080:8080"\n')))

    def test_flags_long_syntax_without_host_ip(self) -> None:
        text = "services:\n  a:\n    ports:\n      - target: 80\n        published: 8080\n"
        self.assertEqual(1, len(self.scan(text)))

    def test_accepts_loopback_forms(self) -> None:
        text = (
            "services:\n  a:\n    ports:\n"
            '      - "127.0.0.1:8080:8080"\n'
            '      - "[::1]:9090:9090"\n'
            "      - target: 80\n        published: 3000\n        host_ip: 127.0.0.1\n"
        )
        self.assertEqual([], self.scan(text))


if __name__ == "__main__":
    unittest.main()
