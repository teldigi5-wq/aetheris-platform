"""Regression guard: the orchestrator memory owner header is never client-controlled.

The runtime MemoryInspectorController scopes every record by X-Aetheris-Owner-Id. The
gateway must strip any client copy on every route and set it only from the verified uid.
"""
from pathlib import Path
import unittest

FILTER = (Path(__file__).resolve().parents[1]
          / "gateway/src/main/java/io/aetheris/gateway/JwtAuthenticationFilter.java")


class GatewayMemoryOwnerBindingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.java = FILTER.read_text(encoding="utf-8")

    def test_owner_header_is_an_identity_header_stripped_everywhere(self) -> None:
        self.assertIn('MEMORY_OWNER_HEADER = "X-Aetheris-Owner-Id"', self.java)
        identity = self.java.split("IDENTITY_HEADERS = List.of(")[1].split(");")[0]
        self.assertIn("MEMORY_OWNER_HEADER", identity)
        self.assertIn("headers.remove(MEMORY_OWNER_HEADER);", self.java)

    def test_owner_is_set_only_from_verified_uid_on_memory_routes(self) -> None:
        self.assertIn('headers.set(MEMORY_OWNER_HEADER, "user:" + ((Number) userId).longValue());', self.java)
        self.assertIn("memoryRoute && isPositiveIntegralUid(userId)", self.java)
        self.assertIn("Memory access requires a verified user identity", self.java)


if __name__ == "__main__":
    unittest.main()
