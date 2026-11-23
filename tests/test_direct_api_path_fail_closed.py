"""Regression guard for the 2026-11-23 direct User/Audit authentication bypass.

CI proved that an unauthenticated GET /%61pi/users returned HTTP 200: the filter skipped
authentication on the raw URI spelling while Tomcat/Spring MVC decoded it to /api/users.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FILTERS = ("user-service/src/main/java/io/aetheris/users/security/DirectApiJwtFilter.java",
           "audit-service/src/main/java/io/aetheris/audit/security/DirectApiJwtFilter.java")
LIVE_TESTS = ("user-service/src/test/java/io/aetheris/users/security/DirectApiLiveHttpTest.java",
              "audit-service/src/test/java/io/aetheris/audit/security/DirectApiLiveHttpTest.java")


class DirectApiPathFailClosedTest(unittest.TestCase):
    def test_skip_decision_is_fail_closed(self) -> None:
        for name in FILTERS:
            with self.subTest(filter=name):
                java = (ROOT / name).read_text(encoding="utf-8")
                self.assertIn('Pattern.compile("[A-Za-z0-9/_.-]*")', java)
                self.assertIn("isApi(path) || isApi(request.getServletPath()) || isApi(request.getPathInfo())", java)
                self.assertIn("toLowerCase(java.util.Locale.ROOT)", java)
                self.assertNotIn("return !(ROOT.equals(path) || path.startsWith(ROOT + \"/\"));", java)

    def test_real_http_bypass_proof_is_kept(self) -> None:
        for name in LIVE_TESTS:
            with self.subTest(test=name):
                java = (ROOT / name).read_text(encoding="utf-8")
                self.assertIn("realHttpEncodedOrMatrixApiPathsCannotBypassAuthentication", java)
                self.assertIn('"/%61pi/', java)
                self.assertIn("verifyNoInteractions", java)


if __name__ == "__main__":
    unittest.main()
