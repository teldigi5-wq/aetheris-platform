"""Gateway rate-limit boundary.

1. The limiter key must never come from a client-controlled header; only from the
   gateway-verified JWT (exchange attribute) or the peer IP.
2. Client-supplied X-Aetheris-* identity headers are stripped on every route.
3. Credential endpoints (login/register, API and browser) have a strict per-IP
   route that is matched before the general identity route.
"""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
GATEWAY = ROOT / "gateway/src/main"
CREDENTIAL_PATHS = ["/api/auth/login", "/api/auth/register", "/api/auth/browser/login", "/api/auth/browser/register"]


def routes() -> list[dict[str, str]]:
    text = (GATEWAY / "resources/application.yml").read_text(encoding="utf-8")
    blocks = re.split(r"(?m)^        - id: ", text.split("routes:", 1)[1])[1:]
    parsed = []
    for block in blocks:
        rid = block.split("\n", 1)[0].strip()
        path = re.search(r"- Path=(\S+)", block)
        rate = re.search(r"replenishRate: (\d+)", block)
        burst = re.search(r"burstCapacity: (\d+)", block)
        parsed.append({"id": rid, "path": path.group(1) if path else "",
                       "rate": rate.group(1) if rate else "", "burst": burst.group(1) if burst else ""})
    return parsed


class GatewayAuthRateLimitTest(unittest.TestCase):
    def test_key_resolver_never_reads_request_headers(self) -> None:
        java = (GATEWAY / "java/io/aetheris/gateway/RateLimitConfig.java").read_text(encoding="utf-8")
        self.assertNotIn("getHeaders()", java)
        self.assertIn("JwtAuthenticationFilter.VERIFIED_USER_ID_ATTRIBUTE", java)

    def test_identity_headers_are_stripped_on_public_routes(self) -> None:
        java = (GATEWAY / "java/io/aetheris/gateway/JwtAuthenticationFilter.java").read_text(encoding="utf-8")
        self.assertIn("return chain.filter(withoutIdentityHeaders(exchange));", java)
        for header in ("X-Aetheris-User-Id", "X-Aetheris-User-Email", "X-Aetheris-User-Role", "X-Aetheris-Scopes"):
            self.assertIn(f'"{header}"', java)

    def test_credential_route_is_strict_and_matched_first(self) -> None:
        parsed = routes()
        ids = [r["id"] for r in parsed]
        self.assertLess(ids.index("identity-credentials"), ids.index("identity-service"))
        credentials = parsed[ids.index("identity-credentials")]
        self.assertEqual(sorted(CREDENTIAL_PATHS), sorted(credentials["path"].split(",")))
        self.assertLessEqual(int(credentials["rate"]), 1)
        self.assertLessEqual(int(credentials["burst"]), 10)

    def test_every_route_has_a_rate_limiter(self) -> None:
        missing = [r["id"] for r in routes() if not r["rate"]]
        self.assertEqual([], missing)


if __name__ == "__main__":
    unittest.main()
