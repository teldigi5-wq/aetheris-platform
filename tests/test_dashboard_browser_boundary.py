import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NGINX = ROOT / "dashboard" / "nginx.conf"
DASHBOARD = ROOT / "dashboard"


class DashboardBrowserBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.nginx = NGINX.read_text(encoding="utf-8")

    def test_only_health_is_proxied_from_browser_actuator_surface(self):
        self.assertRegex(self.nginx, r"location\s*=\s*/actuator/health\s*\{")
        self.assertRegex(
            self.nginx,
            r"location\s+/actuator/\s*\{\s*return\s+404;\s*\}",
        )
        self.assertNotRegex(
            self.nginx,
            r"location\s+/actuator/\s*\{[^}]*proxy_pass",
        )

    def test_dashboard_runtime_only_depends_on_actuator_health(self):
        consumers = []
        for path in (DASHBOARD / "src").rglob("*"):
            if not path.is_file() or path.suffix not in {".ts", ".tsx", ".js", ".jsx"}:
                continue
            text = path.read_text(encoding="utf-8")
            if "/actuator/" in text:
                consumers.append((path.relative_to(ROOT).as_posix(), text))

        self.assertTrue(consumers, "expected the dashboard health consumer to remain explicit")
        for relative_path, text in consumers:
            actuator_paths = set(re.findall(r"/actuator/[A-Za-z0-9_./-]+", text))
            self.assertEqual(
                {"/actuator/health"},
                actuator_paths,
                f"unexpected browser actuator dependency in {relative_path}",
            )

    def test_baseline_browser_security_headers_are_always_emitted(self):
        required = {
            'add_header X-Content-Type-Options "nosniff" always;',
            'add_header X-Frame-Options "DENY" always;',
            'add_header Referrer-Policy "no-referrer" always;',
            'add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=(), usb=()" always;',
        }
        for directive in required:
            self.assertIn(directive, self.nginx)

    def test_csp_restricts_external_content_and_framing(self):
        match = re.search(r'add_header Content-Security-Policy "([^"]+)" always;', self.nginx)
        self.assertIsNotNone(match, "Content-Security-Policy header is required")
        policy = match.group(1)
        for directive in (
            "default-src 'self'",
            "base-uri 'self'",
            "object-src 'none'",
            "frame-ancestors 'none'",
            "form-action 'self'",
            "connect-src 'self'",
        ):
            self.assertIn(directive, policy)


if __name__ == "__main__":
    unittest.main()
